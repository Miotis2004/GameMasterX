package com.gamemasterx.server.ai.operation.validate;

import org.springframework.stereotype.Component;
import com.gamemasterx.server.ai.operation.OperationType;
import com.gamemasterx.server.ai.operation.ProposedOperation;
import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.encounter.model.Encounter.Position;
import com.gamemasterx.server.gameplay.MovementRangeValidationException;
import com.gamemasterx.server.gameplay.service.MovementRangeService;

import java.util.Optional;

/**
 * Enforces the {@link ValidationDimension#RANGE} dimension.
 *
 * <p>A {@link OperationType#MOVE_ACTOR} proposal carries destination grid
 * coordinates in its parameters and may only move the participant within its
 * available movement. The distance is computed and bounded by the backend-owned
 * {@link MovementRangeService}, so the range bound is decided by the
 * deterministic rules engine and never by the caller.</p>
 */

@Component
public class RangeChecker implements OperationChecker {

    private final MovementRangeService movementRangeService;

    /**
     * @param movementRangeService the single authority for movement and reach
     */
    public RangeChecker(MovementRangeService movementRangeService) {
        this.movementRangeService = movementRangeService;
    }

    @Override
    public ValidationDimension dimension() {
        return ValidationDimension.RANGE;
    }

    @Override
    public Optional<OperationValidationError> check(ProposedOperation proposal, ProposedOperationContext context) {
        if (proposal.operationType() != OperationType.MOVE_ACTOR) {
            return Optional.empty();
        }
        Encounter encounter = context.getEncounter();
        if (encounter == null) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.RANGE, "targetId",
                    "the target encounter must be loaded to verify movement range"));
        }
        Encounter.Participant participant = findParticipant(encounter, proposal.targetId());
        if (participant == null) {
            return Optional.empty();
        }
        Position from = participant.getPosition();
        if (from == null) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.RANGE, null,
                    "participant " + proposal.targetId() + " has no grid position to move from"));
        }
        Integer toX = integerParameter(proposal, "toX");
        Integer toY = integerParameter(proposal, "toY");
        if (toX == null || toY == null) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.RANGE, "coordinates",
                    "a move must carry integer destination coordinates 'toX' and 'toY'"));
        }
        try {
            movementRangeService.resolveMovement(
                    from, new Position(toX, toY), participant.getMovementSpeed());
            return Optional.empty();
        } catch (MovementRangeValidationException | IllegalArgumentException ex) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.RANGE, "coordinates", ex.getMessage()));
        }
    }

    private static Encounter.Participant findParticipant(Encounter encounter, String participantId) {
        for (Encounter.Participant p : encounter.getParticipants()) {
            if (participantId != null && participantId.equals(p.getId())) {
                return p;
            }
        }
        return null;
    }

    private static Integer integerParameter(ProposedOperation proposal, String key) {
        Object value = proposal.parameters().get(key);
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
