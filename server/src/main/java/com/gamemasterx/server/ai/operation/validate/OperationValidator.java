package com.gamemasterx.server.ai.operation.validate;

import com.gamemasterx.server.ai.operation.ProposedOperation;
import com.gamemasterx.server.encounter.idempotency.IdempotencyService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The single, backend-authoritative orchestrator that runs a {@link
 * ProposedOperation} through every validation dimension before it is acted on.
 *
 * <p>Every proposed operation must pass <em>all</em> applicable {@link
 * OperationChecker} dimensions &ndash; {@link ValidationDimension#SCHEMA},
 * {@link ValidationDimension#ENTITY_EXISTS}, {@link ValidationDimension#AUTHORIZATION},
 * {@link ValidationDimension#ACTOR_CONTROL}, {@link ValidationDimension#LEGAL_ACTION},
 * {@link ValidationDimension#TARGET}, {@link ValidationDimension#RANGE},
 * {@link ValidationDimension#RESOURCES}, {@link ValidationDimension#EXPECTED_REVISION},
 * {@link ValidationDimension#IDEMPOTENCY}, {@link ValidationDimension#NUMERIC_AGREEMENT}
 * and {@link ValidationDimension#SECRET_DISCLOSURE} &ndash; before the backend will
 * execute it. The orchestrator collects the failure of every dimension that fails
 * into a single {@link OperationValidationResult} so a rejected proposal can be
 * fully explained, and it exposes {@link #validateStrict} which turns any
 * failure into an {@link OperationValidationException} that callers raise before
 * performing any deterministic execution.</p>
 *
 * <p>The checks run in a fixed, backend-owned dependency order
 * ({@link ValidationDimension} ordinal): a proposal is structurally valid before
 * it is checked for existence, existence before authorization, authorization
 * before actor control, and so on. The order is authoritative and
 * backend-declared; a caller cannot influence which dimension is evaluated
 * first. The rules engine remains the sole authority for the {@link
 * ValidationDimension#LEGAL_ACTION} decision, and authorization is enforced by
 * the backend for every proposed operation through {@link
 * ValidationDimension#AUTHORIZATION}.</p>
 *
 * <p>The AI never generates authoritative random results and never mutates the
 * store directly: the only path that persists a proposed change is one that has
 * first been cleared by this orchestrator, so an invalid proposal can never
 * reach deterministic execution.</p>
 */
@Service
public class OperationValidator {

    private final List<OperationChecker> orderedCheckers;
    private final IdempotencyService idempotencyService;

    /**
     * @param checkers            every backend {@link OperationChecker}, one per
     *                            {@link ValidationDimension}
     * @param idempotencyService  the backend-owned store that prevents a
     *                            repeated idempotency key from re-applying its
     *                            effect
     */
    public OperationValidator(List<OperationChecker> checkers, IdempotencyService idempotencyService) {
        List<OperationChecker> sorted = new ArrayList<>(checkers);
        sorted.sort((a, b) -> Integer.compare(a.dimension().ordinal(), b.dimension().ordinal()));
        this.orderedCheckers = Collections.unmodifiableList(sorted);
        this.idempotencyService = idempotencyService;
    }

    /**
     * Runs every applicable validation dimension and aggregates the verdicts.
     *
     * @param proposal  the proposal being validated
     * @param context   the runtime context the checks are evaluated against
     * @return a result collecting every dimension that failed, together with
     *         whether a supplied idempotency key had already completed a
     *         successful operation
     */
    public OperationValidationResult validate(ProposedOperation proposal, ProposedOperationContext context) {
        List<OperationValidationError> errors = new ArrayList<>();
        for (OperationChecker checker : orderedCheckers) {
            Optional<OperationValidationError> error = checker.check(proposal, context);
            error.ifPresent(errors::add);
        }
        // A repeated idempotency key is not itself a validation error, but it
        // must never re-apply its effect, so it is only reported once the
        // proposal is otherwise valid.
        boolean idempotentRepeat = errors.isEmpty() && idempotentRepeat(context);
        return OperationValidationResult.of(errors, idempotentRepeat);
    }

    /**
     * Runs every applicable validation dimension and rejects the proposal when
     * any of them fails, or when a supplied idempotency key has already
     * completed a successful operation.
     *
     * @param proposal  the proposal being validated
     * @param context   the runtime context the checks are evaluated against
     * @throws OperationValidationException when the proposal fails any
     *                                      dimension or is a repeated idempotent
     *                                      key; raised before any deterministic
     *                                      execution
     */
    public void validateStrict(ProposedOperation proposal, ProposedOperationContext context) {
        OperationValidationResult result = validate(proposal, context);
        if (result.hasErrors() || result.isIdempotentRepeat()) {
            throw new OperationValidationException(result.errors());
        }
    }

    private boolean idempotentRepeat(ProposedOperationContext context) {
        String key = context.idempotencyKey();
        return (key != null && !key.isBlank() && idempotencyService.isCompleted(key));
    }
}
