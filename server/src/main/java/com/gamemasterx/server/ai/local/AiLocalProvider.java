package com.gamemasterx.server.ai.local;

import com.gamemasterx.server.ai.AiCancellation;
import com.gamemasterx.server.ai.AiGenerationRequest;
import com.gamemasterx.server.ai.AiGenerationResult;
import com.gamemasterx.server.ai.AiProvider;
import com.gamemasterx.server.ai.AiProviderException;
import com.gamemasterx.server.ai.config.AiProviderProperties;

/**
 * Bundled offline AI provider.
 *
 * <p>This provider requires no cloud AI provider, no network access and no
 * credentials. It produces deterministic, clearly-marked placeholder text so
 * the application can build, run and be exercised end-to-end out of the box.
 *
 * <p>When an external {@code endpoint} is configured, a real provider backed by
 * that endpoint should be supplied as the primary {@link AiProvider} bean and
 * this local provider becomes a documented fallback. The auto-configuration
 * selects this provider whenever no external endpoint is configured.</p>
 */
public class AiLocalProvider implements AiProvider {

    private static final String PREFIX = "[local AI · ";

    private final String model;
    private final int contextSize;
    private final int maxTokens;

    public AiLocalProvider(AiProviderProperties properties) {
        this.model = properties.getModel();
        this.contextSize = properties.getContextSize();
        this.maxTokens = Math.max(1, properties.getMaxTokens());
    }

    @Override
    public String providerName() {
        return "local";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public int contextSize() {
        return contextSize;
    }

    @Override
    public int maxTokens() {
        return maxTokens;
    }

    @Override
    public boolean isAvailable() {
        // Always usable offline; no socket or credential checks required.
        return true;
    }

    @Override
    public AiGenerationResult generate(AiGenerationRequest request, AiCancellation cancellation) {
        if (request == null || request.instruction() == null) {
            throw new AiProviderException("AI generation request or instruction must not be null");
        }
        if (cancellation != null && cancellation.isCancellationRequested()) {
            throw new AiProviderException("AI generation cancelled before it started");
        }

        // Respect cooperative cancellation so a slow or queued completion can be
        // aborted without waiting for the overall timeout.
        if (cancellation != null) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiProviderException("AI generation interrupted", e);
            }
            if (cancellation.isCancellationRequested()) {
                throw new AiProviderException("AI generation cancelled");
            }
        }

        int outputTokens = Math.min(maxTokens, Math.max(1, request.instruction().length() / 4 + 1));
        String text = PREFIX + request.instruction() + " ]";
        int inputTokens = Math.max(1, request.instruction().length() / 4);

        return new AiGenerationResult(text, model, inputTokens, outputTokens);
    }
}
