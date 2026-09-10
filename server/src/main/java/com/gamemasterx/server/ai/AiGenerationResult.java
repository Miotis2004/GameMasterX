package com.gamemasterx.server.ai;

/**
 * The result of a single {@link AiProvider} completion.
 *
 * <p>Alongside the generated text it records how many input and output tokens
 * were consumed so callers and health checks can stay within the configured
 * context size and generation limit.</p>
 */
public record AiGenerationResult(
        String text,
        String model,
        int inputTokens,
        int outputTokens) {

    /**
     * @return total tokens consumed by this completion
     */
    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
