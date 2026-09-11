package com.gamemasterx.server.ai.operation;

import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.encounter.model.EncounterDto;
import com.gamemasterx.server.encounter.service.EncounterService;
import com.gamemasterx.server.ai.operation.validate.OperationValidator;
import com.gamemasterx.server.ai.operation.validate.ProposedOperationContext;
import com.gamemasterx.server.gameplay.model.Audit;
import com.gamemasterx.server.gameplay.model.MutationDecision;
import com.gamemasterx.server.gameplay.service.GameplayAuditService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The single backend-authoritative path that turns an {@link ProposedOperation}
 * into a deterministic change of encounter state.
 *
 * <p>A proposal is only ever acted on after it has passed <em>every</em>
 * applicable validation dimension through the orchestrating {@link
 * OperationValidator}. The validator enforces schema, entity existence,
 * authorization, actor control, legal action, target, range, resources,
 * expected revision, idempotency, numeric agreement and secret disclosure, and
 * the deterministic rules engine remains the sole authority for the legal-action
 * decision. Because {@link #execute} raises {@link
 * com.gamemasterx.server.ai.operation.validate.OperationValidationException}
 * before any state is mutated, an invalid proposal can never reach deterministic
 * execution and the AI can never mutate the store directly.</p>
 *
 * <p>Once a proposal is cleared, the concrete state change is applied through the
 * backend-owned {@link EncounterService}, which owns the hit-point, resource and
 * movement rules and commits through the guarded, idempotent coordinator. The
 * dispatch from {@link OperationType} to a service method is backend-declared
 * here and is never influenced by the caller-supplied proposal.</p>
 */
@Service
public class ProposedOperationExecutionService {

    private final EncounterService encounterService;
    private final OperationValidator validator;
    private final GameplayAuditService gameplayAuditService;

    /**
     * @param encounterService the backend authority for encounter state mutation
     * @param validator        the backend-authoritative validation orchestrator
     * @param gameplayAuditService the append-only audit service
     */
    public ProposedOperationExecutionService(EncounterService encounterService,
                                             OperationValidator validator,
                                             GameplayAuditService gameplayAuditService) {
        this.encounterService = encounterService;
        this.validator = validator;
        this.gameplayAuditService = gameplayAuditService;
    }

    /**
     * Validates a proposed operation against the backend-authoritative dimensions
     * and, only when it is valid, applies it to the owning encounter.
     *
     * <p>The proposal is validated before any execution: the owning encounter is
     * loaded once through {@link EncounterService#loadForOperation} (which also
     * asserts the caller may access it), every validation dimension is run
     * against the same loaded document, and {@link OperationValidator#validateStrict}
     * raises on the first failing dimension. Only a fully cleared proposal is
     * dispatched to the deterministic {@link EncounterService} mutation.</p>
     *
     * @param encounterId    the stable identifier of the owning encounter
     * @param request        the transport request describing the proposal
     * @param expectedRevision the revision the caller expects, or {@code null}
     * @param idempotencyKey   the caller-supplied idempotency key, or {@code null}
     * @param actor          the authenticated caller
     * @param correlationId   the correlation identifier for tracing, or {@code null}
     * @return the updated encounter
     * @throws OperationValidationException when the proposal fails any
     *                                      validation dimension or is a repeated
     *                                      idempotent key; raised before execution
     */
    public EncounterDto execute(String encounterId, ProposedOperationRequest request,
                                Long expectedRevision, String idempotencyKey, String actor,
                                String correlationId) {
        ProposedOperation proposal = toOperation(request);

        Encounter encounter = encounterService.loadForOperation(encounterId, actor);
        int revisionBefore = encounter.getRevision();
        ProposedOperationContext context = new ProposedOperationContext(
                actor,
                encounter.getCampaignId(),
                encounterId,
                expectedRevision,
                idempotencyKey,
                encounter,
                null);

        try {
            validator.validateStrict(proposal, context);
        } catch (com.gamemasterx.server.ai.operation.validate.OperationValidationException ex) {
            recordRejectedProposalAudit(encounter, proposal, actor, revisionBefore, correlationId, ex.getMessage());
            throw ex;
        }

        return dispatch(encounterId, proposal, actor, expectedRevision, idempotencyKey);
    }

    /**
     * Maps a validated {@link OperationType} to its deterministic backend
     * service method. The mapping is entirely backend-owned; the caller may name
     * an operation type but cannot rewrite what each one does.
     */
    private EncounterDto dispatch(String encounterId, ProposedOperation proposal,
                                  String actor, Long expectedRevision, String idempotencyKey) {
        Map<String, Object> params = proposal.parameters();
        String justification = proposal.justification();
        switch (proposal.operationType()) {
            case APPLY_DAMAGE: {
                int amount = nonNullInt(params, "amount");
                return encounterService.applyDamage(
                        encounterId, proposal.targetId(), amount, justification, actor,
                        expectedRevision, idempotencyKey);
            }
            case APPLY_HEALING: {
                int amount = nonNullInt(params, "amount");
                return encounterService.applyHealing(
                        encounterId, proposal.targetId(), amount, justification, actor,
                        expectedRevision, idempotencyKey);
            }
            case APPLY_CONDITION: {
                String name = nonNullString(params, "name");
                String description = stringParam(params, "description");
                Integer roundsRemaining = intParam(params, "roundsRemaining");
                return encounterService.addCondition(
                        encounterId, proposal.targetId(), name, description,
                        roundsRemaining, actor);
            }
            case REMOVE_CONDITION: {
                String name = nonNullString(params, "name");
                return encounterService.removeCondition(
                        encounterId, proposal.targetId(), name, actor);
            }
            case MOVE_ACTOR: {
                int toX = nonNullInt(params, "toX");
                int toY = nonNullInt(params, "toY");
                return encounterService.moveParticipant(
                        encounterId, proposal.targetId(), toX, toY, justification, actor);
            }
            case CHANGE_RESOURCE: {
                String resourceName = nonNullString(params, "resourceName");
                int delta = nonNullInt(params, "delta");
                return encounterService.changeResource(
                        encounterId, proposal.targetId(), resourceName, delta,
                        justification, actor, idempotencyKey);
            }
            case SET_STATE: {
                String stateName = nonNullString(params, "stateName");
                Boolean value = boolParam(params, "value");
                return encounterService.setState(
                        encounterId, proposal.targetId(), stateName, value,
                        justification, actor, idempotencyKey);
            }
            case NARRATE:
                return encounterService.applyNarrative(
                        encounterId, justification, actor, expectedRevision, idempotencyKey);
            default:
                throw new IllegalArgumentException(
                        "No deterministic backend execution is defined for operation type "
                                + proposal.operationType());
        }
    }

    /**
     * Resolves the transport request into the immutable backend
     * {@link ProposedOperation}, converting the wire {@link OperationType} name.
     */
    static ProposedOperation toOperation(ProposedOperationRequest request) {
        OperationType type;
        if (request.operationType() == null || request.operationType().isBlank()) {
            throw new IllegalArgumentException(
                    "A proposed operation must declare an operation type");
        }
        try {
            type = OperationType.valueOf(request.operationType().trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "operation type '" + request.operationType() + "' is not a recognised operation type");
        }
        return new ProposedOperation(
                request.schemaVersion(),
                type,
                request.targetKind(),
                request.targetId(),
                request.targetName(),
                request.parameters(),
                request.justification(),
                request.priority(),
                request.constraints());
    }

    private static String nonNullString(Map<String, Object> params, String key) {
        String value = stringParam(params, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A " + key + " value is required");
        }
        return value;
    }

    private static int nonNullInt(Map<String, Object> params, String key) {
        Integer value = intParam(params, key);
        if (value == null) {
            throw new IllegalArgumentException("A " + key + " value is required");
        }
        return value;
    }

    private static String stringParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return (value instanceof String s) ? s : null;
    }

    private static Integer intParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static Boolean boolParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        return null;
    }

    private void recordRejectedProposalAudit(Encounter encounter, ProposedOperation proposal, String actor, int revisionBefore, String correlationId, String reason) {
        String campaignId = encounter.getCampaignId();
        String encounterId = encounter.getId();
        String subjectType = proposal.operationType().name();
        String subjectId = proposal.targetId();
        String before = "schemaVersion=" + proposal.schemaVersion()
                + ", operationType=" + proposal.operationType()
                + ", targetKind=" + proposal.targetKind()
                + ", targetId=" + proposal.targetId()
                + ", targetName=" + proposal.targetName()
                + ", parameters=" + proposal.parameters()
                + ", justification=" + proposal.justification()
                + ", priority=" + proposal.priority()
                + ", constraints=" + proposal.constraints();
        Audit audit = new Audit(
                null,
                0L,
                campaignId,
                encounterId,
                null,
                null,
                null,
                subjectType,
                subjectId,
                before,
                null,
                MutationDecision.REJECTED,
                actor,
                reason,
                revisionBefore,
                revisionBefore,
                Instant.now(),
                correlationId);
        gameplayAuditService.appendAuditEntries(List.of(audit));
    }
}
