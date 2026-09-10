package com.gamemasterx.server.ai.operation.validate;

import java.util.Objects;

/**
 * A single, dimensionally-tagged validation failure.
 *
 * <p>Every failure carries the {@link ValidationDimension} that failed, the
 * offending field name (when one applies) and a human-readable diagnostic, so a
 * rejected proposal can be explained unambiguously at the API boundary.</p>
 */
public record OperationValidationError(
        ValidationDimension dimension,
        String field,
        String message) {

    public OperationValidationError(ValidationDimension dimension, String message) {
        this(dimension, null, message);
    }

    public OperationValidationError {
        Objects.requireNonNull(dimension, "dimension is required");
        Objects.requireNonNull(message, "message is required");
    }

    @Override
    public String toString() {
        return dimension + (field != null ? "(" + field + ")" : "") + ": " + message;
    }
}
