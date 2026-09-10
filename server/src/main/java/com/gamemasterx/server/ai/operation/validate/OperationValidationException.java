package com.gamemasterx.server.ai.operation.validate;

import java.util.List;

/**
 * Raised when a proposed operation fails one or more validation dimensions.
 *
 * <p>This is a validation failure, never a programming error, so callers map it
 * to a {@code 400 BAD_REQUEST} rather than a {@code 500}. The exception carries
 * every dimension that failed, and a concise summary message that names the
 * primary (first) failure so a rejected proposal can be explained clearly.</p>
 */
public class OperationValidationException extends RuntimeException {

    private final List<OperationValidationError> errors;

    /**
     * @param errors the collected validation failures
     */
    public OperationValidationException(List<OperationValidationError> errors) {
        super(summary(errors));
        this.errors = List.copyOf(errors);
    }

    /**
     * @return the immutable list of validation failures, in the order the
     * dimensions were checked
     */
    public List<OperationValidationError> errors() {
        return errors;
    }

    private static String summary(List<OperationValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "The proposed operation failed validation";
        }
        StringBuilder sb = new StringBuilder("The proposed operation failed validation: ");
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(errors.get(i));
        }
        return sb.toString();
    }
}
