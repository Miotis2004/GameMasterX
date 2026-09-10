package com.gamemasterx.server.ai;

/**
 * Thrown when an {@link AiProvider} completion fails for a reason other than a
 * timeout or cancellation.
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
