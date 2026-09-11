package com.gamemasterx.server.tactical.validation;

/**
 * Thrown when tactical map validation fails.
 */
public class TacticalMapValidationException extends RuntimeException {
    public TacticalMapValidationException(String message) {
        super(message);
    }
}
