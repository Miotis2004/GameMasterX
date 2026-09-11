package com.gamemasterx.server.narration.controller;

import com.gamemasterx.server.ai.AiGenerationResult;
import com.gamemasterx.server.ai.AiGenerationService;
import com.gamemasterx.server.ai.AiGenerationRequest;
import com.gamemasterx.server.ai.AiProviderException;
import com.gamemasterx.server.exception.AuthorizationException;
import com.gamemasterx.server.exception.ErrorResponse;
import com.gamemasterx.server.filter.AuthFilter;
import com.gamemasterx.server.narration.model.NarrationContext;
import com.gamemasterx.server.narration.model.NarrationInput;
import com.gamemasterx.server.narration.model.NarrativeMessage;
import com.gamemasterx.server.narration.model.NarrativeMessageType;
import com.gamemasterx.server.narration.service.NarrationContextAssembler;
import com.gamemasterx.server.narration.service.NarrativePersistenceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AI narration context-assembly endpoints.
 *
 * <p>These endpoints expose the narration bounded context's assembly operation.
 * Every request is gated by backend authorization: the {@link AuthFilter}
 * establishes the authenticated user id on the request, and the
 * {@link NarrationContextAssembler} enforces the required membership role in the
 * owning campaign before a single context line is produced.</p>
 *
 * <p>The strict player-vs-GM separation is enforced inside the assembler and
 * honoured by the generation endpoint: only
 * {@link NarrationContext#playerVisibleContextLines()} are ever forwarded to the
 * AI. The {@link NarrationContext#gmSecretContextLines() GM-only secret lines}
 * are returned to the calling game master in the response but are never sent to
 * the AI.</p>
 */
@RestController
@RequestMapping("/api/narration")
public class NarrationController {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private final NarrationContextAssembler assembler;
    private final AiGenerationService aiGenerationService;
    private final NarrativePersistenceService narrativePersistenceService;

    public NarrationController(NarrationContextAssembler assembler, AiGenerationService aiGenerationService, NarrativePersistenceService narrativePersistenceService) {
        this.assembler = assembler;
        this.aiGenerationService = aiGenerationService;
        this.narrativePersistenceService = narrativePersistenceService;
    }

    /**
     * Assembles the player/GM-separated, size-bounded narration context for the
     * given sources. The assembled context carries both partitions: the
     * player-visible lines (safe to forward to the AI) and the GM-only secret
     * lines (returned to the caller only). The {@link
     * NarrationContext#separationVerified()} flag records that the two
     * partitions were checked for overlap, and the
     * {@link NarrationContext#playerContextBounded()} / {@link
     * NarrationContext#boundNote()} fields report whether the player-visible
     * context had to be truncated.
     *
     * <p>Role requirement: the caller must hold at least the configured
     * required role ({@code PLAYER} by default) in the owning campaign.</p>
     */
    @PostMapping("/assemble")
    public ResponseEntity<NarrationContext> assemble(
            @RequestBody NarrationAssembleRequest request,
            HttpServletRequest httpRequest) {
        String actor = requireActor(httpRequest);
        NarrationInput input = request.toInput(actor);
        NarrationContext context = assembler.assemble(input);
        return ResponseEntity.ok(context);
    }

    /**
     * Assembles the context and produces a single AI narration completion from
     * the player-visible partition only. GM-only secrets are assembled and
     * separated but never forwarded to the AI. The caller receives the
     * generated text plus a summary of the context that actually drove the
     * completion.
     *
     * <p>Role requirement: the caller must hold at least the configured
     * required role ({@code PLAYER} by default) in the owning campaign.</p>
     */
    @PostMapping("/generate")
    public ResponseEntity<NarrationGenerationResponse> generate(
            @RequestBody NarrationAssembleRequest request,
            HttpServletRequest httpRequest) {
        String actor = requireActor(httpRequest);
        NarrationInput input = request.toInput(actor);
        NarrationContext context = assembler.assemble(input);

        if (!context.separationVerified()) {
            throw new IllegalStateException(
                    "Narration context separation check failed; refusing to send context to the AI");
        }

        AiGenerationRequest generationRequest = new AiGenerationRequest(
                context.instruction(), context.playerVisibleContextLines(), null);
        AiGenerationResult result = aiGenerationService.generate(generationRequest);

        return ResponseEntity.ok(new NarrationGenerationResponse(
                result.text(),
                context.playerVisibleLineCount(),
                context.gmSecretLineCount(),
                context.playerContextBytes(),
                context.playerContextBounded()));
    }

    @PostMapping("/stream")
    public SseEmitter stream(
            @RequestBody NarrationAssembleRequest request,
            HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        String turnId = UUID.randomUUID().toString();
        int sequence = 0;

        try {
            String actor = requireActor(httpRequest);
            NarrationInput input = request.toInput(actor);
            NarrationContext context = assembler.assemble(input);

            if (!context.separationVerified()) {
                throw new IllegalStateException("Narration context separation check failed");
            }

            AiGenerationRequest generationRequest = new AiGenerationRequest(
                    context.instruction(), context.playerVisibleContextLines(), null);
            AiGenerationResult result = aiGenerationService.generate(generationRequest);

            sequence++;
            String startPayload = "{\"turnId\":" + turnId + "\",\"sequenceNumber\":" + sequence + ",\"type\":\"start\"}";
            emitter.send(SseEmitter.event().name("narration").data(startPayload));

            String text = result.text();
            int chunkSize = 40;
            for (int i = 0; i < text.length(); i += chunkSize) {
                int end = Math.min(i + chunkSize, text.length());
                String chunk = text.substring(i, end);
                sequence++;
                String deltaPayload = "{\"turnId\":" + turnId + "\",\"sequenceNumber\":" + sequence + ",\"type\":\"delta\",\"delta\":\"" + escapeJson(chunk) + "\"}";
                emitter.send(SseEmitter.event().name("narration").data(deltaPayload));
            }

            sequence++;
            String endPayload = "{\"turnId\":" + turnId + "\",\"sequenceNumber\":" + sequence + ",\"type\":\"end\"}";
            emitter.send(SseEmitter.event().name("narration").data(endPayload));

            // Persist completed narrative durably after streaming completes
            try {
                NarrativeMessage message = new NarrativeMessage(
                        UUID.randomUUID().toString(),
                        NarrativeMessageType.NARRATIVE,
                        text,
                        actor,
                        Instant.now(),
                        false
                );
                narrativePersistenceService.persistCompleted(request.campaignId(), request.encounterId(), turnId, List.of(message));
            } catch (Exception ignored) {
                // Persistence failures should not interrupt streaming
            }

            emitter.complete();
        } catch (Exception ex) {
            try {
                String errorPayload = "{\"turnId\":" + turnId + "\",\"sequenceNumber\":" + sequence + ",\"type\":\"error\",\"message\":\"" + escapeJson(ex.getMessage() != null ? ex.getMessage() : "unknown") + "\"}";
                emitter.send(SseEmitter.event().name("error").data(errorPayload));
            } catch (Exception ignored) {}
            emitter.completeWithError(ex);
        }

        emitter.onCompletion(() -> {});
        emitter.onTimeout(() -> {});
        return emitter;
    }

    private String requireActor(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthFilter.AUTH_USER_ATTR);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new AuthorizationException(
                com.gamemasterx.server.campaign.membership.model.MembershipRole.OBSERVER,
                "Authentication required to assemble a narration context");
    }

    /**
     * Maps an authorization denial to a consistent {@code 403 Forbidden}
     * response.
     */
    @ExceptionHandler(AuthorizationException.class)
    @Order(2)
    public ResponseEntity<ErrorResponse> handleAuthorization(AuthorizationException ex,
                                                           HttpServletRequest request) {
        String roleSuffix = ex.getRequiredRole() != null
                ? ": requires " + ex.getRequiredRole().name() : "";
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(
                "FORBIDDEN", ex.getMessage() + roleSuffix, correlationId(request), null));
    }

    /**
     * Maps an AI provider failure to a {@code 502 Bad Gateway} response so a
     * downstream provider outage is distinguishable from a client error.
     */
    @ExceptionHandler(AiProviderException.class)
    @Order(3)
    public ResponseEntity<ErrorResponse> handleAiProvider(AiProviderException ex,
                                                          HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(
                "AI_PROVIDER_ERROR", ex.getMessage() == null ? "AI generation failed" : ex.getMessage(),
                correlationId(request), null));
    }

    private static String correlationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * The client-facing request for a narration assembly or generation. The
     * authenticated actor is supplied by the controller from the request
     * context and is never accepted from the wire, so a caller cannot forge its
     * own authority.
     *
     * @param campaignId             the owning campaign, or {@code null}
     * @param encounterId            the active encounter, or {@code null}
     * @param currentSceneId         the in-progress scene, or {@code null}
     * @param actingCharacterId      the acting character, or {@code null}
     * @param visibleTargetCharacterIds the characters visible to the acting
     *                                  character (may be empty or {@code null})
     * @param instruction            the completion instruction, or {@code null}
     */
    public record NarrationAssembleRequest(
            String campaignId,
            String encounterId,
            String currentSceneId,
            String actingCharacterId,
            List<String> visibleTargetCharacterIds,
            String instruction) {

        /**
         * Builds the assembly input, injecting the authenticated actor.
         *
         * @param actor the authenticated caller from the request context
         * @return the assembly input
         */
        public NarrationInput toInput(String actor) {
            return new NarrationInput(
                    campaignId, encounterId, currentSceneId, actingCharacterId,
                    visibleTargetCharacterIds, instruction, actor);
        }
    }

    /**
     * The response for a generation request: the generated narration text plus a
     * summary of the context that actually drove the completion. The summary
     * reports how many player-visible lines were forwarded to the AI, how many
     * GM-only secret lines were assembled and kept from the AI, the byte size of
     * the forwarded context and whether it had to be truncated.
     *
     * @param text                the AI-generated narration
     * @param playerVisibleLines  the number of player-visible lines forwarded
     * @param gmSecretLines       the number of GM-only secret lines kept from the AI
     * @param playerContextBytes  the UTF-8 size of the forwarded context
     * @param playerContextBounded whether the forwarded context was truncated
     */
    public record NarrationGenerationResponse(
            String text,
            int playerVisibleLines,
            int gmSecretLines,
            int playerContextBytes,
            boolean playerContextBounded) {
    }

}
