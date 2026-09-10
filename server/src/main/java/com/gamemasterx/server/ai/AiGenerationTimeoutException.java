package com.gamemasterx.server.ai;

/**
 * Thrown when a completion exceeds the configured generation timeout.
 */
public class AiGenerationTimeoutException extends AiProviderException {

    public AiGenerationTimeoutException(long timeoutMillis) {
        super("AI generation exceeded the configured timeout of " + timeoutMillis + "ms");
    }
}
