package com.gamemasterx.server.ai.operation.validate;

import com.gamemasterx.server.ai.operation.ProposedOperation;
import java.util.Optional;

/**
 * A single, focused validation dimension applied to a {@link ProposedOperation}.
 *
 * <p>Each checker owns exactly one {@link ValidationDimension} and is a pure,
 * stateless function of the proposal and its {@link ProposedOperationContext}.
 * A checker returns {@link Optional#empty()} when the proposal satisfies the
 * dimension and a populated {@link OperationValidationError} otherwise. The
 * orchestrating {@link OperationValidator} runs the checks in dependency order
 * and aggregates their verdicts into a single {@link OperationValidationResult}.
 */
public interface OperationChecker {

    /**
     * @return the dimension this checker enforces
     */
    ValidationDimension dimension();

    /**
     * Apply the check.
     *
     * @param proposal  the proposal being validated
     * @param context   the runtime context for the proposal
     * @return an error when the proposal fails the dimension, otherwise empty
     */
    Optional<OperationValidationError> check(ProposedOperation proposal, ProposedOperationContext context);
}
