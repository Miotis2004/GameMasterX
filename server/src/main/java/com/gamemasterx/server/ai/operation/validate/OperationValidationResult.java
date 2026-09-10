package com.gamemasterx.server.ai.operation.validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The outcome of running a proposed operation through every validation
 * dimension.
 *
 * <p>Unlike {@link OperationValidationException}, which is thrown on the first
 * failing proposal, a result object collects <em>every</em> dimension that
 * failed so the rejection can be fully explained. It also carries the
 * {@link #idempotentRepeat()} flag, which is set when a supplied idempotency key
 * has already completed a successful operation: this is not an error, but it
 * tells the caller that re-running the operation would be a no-op.</p>
 */
public final class OperationValidationResult {

    private final List<OperationValidationError> errors;
    private final boolean idempotentRepeat;

    private OperationValidationResult(List<OperationValidationError> errors, boolean idempotentRepeat) {
        this.errors = errors;
        this.idempotentRepeat = idempotentRepeat;
    }

    /**
     * @param errors the collected failures (may be empty)
     * @param idempotentRepeat whether a supplied idempotency key had already completed
     * @return a result, never {@code null}
     */
    public static OperationValidationResult of(List<OperationValidationError> errors,
                                               boolean idempotentRepeat) {
        return new OperationValidationResult(
                errors == null ? Collections.emptyList() : new ArrayList<>(errors),
                idempotentRepeat);
    }

    /**
     * @return {@code true} when no validation dimension failed
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * @return {@code true} when at least one validation dimension failed
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * @return the immutable list of validation failures, in check order
     */
    public List<OperationValidationError> errors() {
        return errors;
    }

    /**
     * @return {@code true} when the supplied idempotency key had already
     * completed a successful operation and re-running would be a no-op
     */
    public boolean isIdempotentRepeat() {
        return idempotentRepeat;
    }
}
