package com.gamemasterx.server.ai.operation.validate;

import org.springframework.stereotype.Component;
import com.gamemasterx.server.ai.operation.ProposedOperation;

import java.util.Optional;

/**
 * Enforces the {@link ValidationDimension#IDEMPOTENCY} dimension.
 *
 * <p>A supplied idempotency key, when present, must be a non-blank token. The
 * deduplication itself (a repeated key returning the stored outcome instead of
 * re-applying its effect) is enforced by the orchestrator via the backend-owned
 * idempotency store, so a repeated key can never re-apply its effect.</p>
 */

@Component
public class IdempotencyChecker implements OperationChecker {

    @Override
    public ValidationDimension dimension() {
        return ValidationDimension.IDEMPOTENCY;
    }

    @Override
    public Optional<OperationValidationError> check(ProposedOperation proposal, ProposedOperationContext context) {
        String key = context.idempotencyKey();
        if (key != null && key.isBlank()) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.IDEMPOTENCY, "idempotencyKey",
                    "an idempotency key must not be blank when supplied"));
        }
        return Optional.empty();
    }
}
