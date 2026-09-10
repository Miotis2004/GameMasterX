package com.gamemasterx.server.ai;

import java.util.List;
import java.util.Map;

/**
 * An immutable request sent to an {@link AiProvider} for a single completion.
 *
 * <p>The request is transport-agnostic: the abstraction never carries provider
 * specific wire details such as authentication headers or API keys. Those live
 * inside the provider implementation and are configured exclusively on the
 * server through environment variables or local configuration.</p>
 */
public record AiGenerationRequest(
        String instruction,
        List<String> contextLines,
        Map<String, Object> parameters) {

    public AiGenerationRequest {
        contextLines = contextLines != null ? List.copyOf(contextLines) : List.of();
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }

    /**
     * @param instruction the prompt / instruction driving the completion
     * @return a request containing only the instruction
     */
    public static AiGenerationRequest of(String instruction) {
        return new AiGenerationRequest(instruction, List.of(), null);
    }
}
