package com.gamemasterx.server.ai.operation.validate;

import org.springframework.stereotype.Component;
import com.gamemasterx.server.ai.operation.OperationType;
import com.gamemasterx.server.ai.operation.ProposedOperation;

import java.util.Optional;
import java.util.Set;

/**
 * Enforces the {@link ValidationDimension#TARGET} dimension.
 *
 * <p>The target must be well-formed for the proposed operation type: the
 * operation must name a subject, and the {@code targetKind} must name the kind
 * of state that the operation actually changes. Participant-scoped operations
 * ({@link OperationType#APPLY_DAMAGE}, {@link OperationType#APPLY_HEALING},
 * {@link OperationType#APPLY_CONDITION}, {@link OperationType#REMOVE_CONDITION},
 * {@link OperationType#MOVE_ACTOR}, {@link OperationType#CHANGE_RESOURCE} and
 * {@link OperationType#SET_STATE}) require a {@code targetId} and a participant
 * style {@code targetKind}; {@link OperationType#NARRATE} may omit the target
 * entirely, since it adds narrative context around an action rather than
 * changing a subject.</p>
 */

@Component
public class TargetChecker implements OperationChecker {

    /** Kind names that identify a participant-scoped target. */
    private static final Set<String> PARTICIPANT_KINDS = Set.of(
            "participant", "actor", "combatant", "creature");

    @Override
    public ValidationDimension dimension() {
        return ValidationDimension.TARGET;
    }

    @Override
    public Optional<OperationValidationError> check(ProposedOperation proposal, ProposedOperationContext context) {
        if (proposal.operationType() == OperationType.NARRATE) {
            // Narrative context need not address a specific subject.
            return Optional.empty();
        }

        if (proposal.targetId() == null || proposal.targetId().isBlank()) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.TARGET, "targetId",
                    "operation type " + proposal.operationType() + " must target a participant"));
        }

        String kind = proposal.targetKind();
        if (kind == null || PARTICIPANT_KINDS.stream().noneMatch(k -> k.equalsIgnoreCase(kind))) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.TARGET, "targetKind",
                    "operation type " + proposal.operationType() + " must target a participant "
                            + "(expected one of " + PARTICIPANT_KINDS + ")"));
        }
        return Optional.empty();
    }
}
