package com.gamemasterx.server.ai.operation;

/**
 * Raised when a {@link ProposedOperation} does not conform to the
 * {@link OperationSchema} that applies to its declared {@link
 * OperationSchemaVersion}.
 *
 * <p>This is a validation failure, never a programming error, so callers map it
 * to a {@code 400 BAD_REQUEST} rather than a {@code 500}. The exception carries
 * the offending {@link #getField()} so a rejected proposal can be explained
 * unambiguously.</p>
 */
public class OperationSchemaException extends RuntimeException {

    private final String field;

    /**
     * @param field the offending field, or {@code null} when not applicable
     * @param message a human-readable description of the violation
     */
    public OperationSchemaException(String field, String message) {
        super((field == null || field.isBlank()) ? message : field + ": " + message);
        this.field = (field == null || field.isBlank()) ? null : field;
    }

    /**
     * @return the offending field name, or {@code null} when not applicable
     */
    public String getField() {
        return field;
    }
}
