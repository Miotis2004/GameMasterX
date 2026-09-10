package com.gamemasterx.server.ai.operation.validate;

import org.springframework.stereotype.Component;
import com.gamemasterx.server.ai.operation.OperationType;
import com.gamemasterx.server.ai.operation.ProposedOperation;
import com.gamemasterx.server.encounter.repository.EncounterRepository;
import com.gamemasterx.server.encounter.model.Encounter;

import java.util.Optional;

/**
 * Enforces the {@link ValidationDimension#ENTITY_EXISTS} dimension.
 *
 * <p>The subject of the proposal must exist: the owning encounter must exist
 * and, for participant-scoped operations, the participant named by
 * {@code targetId} must be one of the encounter's participants. This is the
 * backend-authoritative existence check, so a proposal that targets a missing
 * encounter or participant is rejected before any execution.</p>
 */

@Component
public class EntityExistenceChecker implements OperationChecker {

    private final EncounterRepository encounterRepository;

    /**
     * @param encounterRepository the authoritative store of encounters
     */
    public EntityExistenceChecker(EncounterRepository encounterRepository) {
        this.encounterRepository = encounterRepository;
    }

    @Override
    public ValidationDimension dimension() {
        return ValidationDimension.ENTITY_EXISTS;
    }

    @Override
    public Optional<OperationValidationError> check(ProposedOperation proposal, ProposedOperationContext context) {
        if (proposal.operationType() == OperationType.NARRATE) {
            // Narrative context is not tied to a specific stored subject.
            return Optional.empty();
        }

        if (context.encounterId() == null || context.encounterId().isBlank()) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.ENTITY_EXISTS, "encounterId",
                    "a participant-scoped operation must name the encounter it targets"));
        }

        Encounter encounter = context.getEncounter();
        if (encounter == null) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.ENTITY_EXISTS, "encounterId",
                    "the target encounter '" + context.encounterId() + "' could not be loaded"));
        }

        if (!encounter.getId().equals(context.encounterId())) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.ENTITY_EXISTS, "encounterId",
                    "the target encounter '" + context.encounterId() + "' does not exist"));
        }

        String participantId = proposal.targetId();
        boolean exists = false;
        for (Encounter.Participant p : encounter.getParticipants()) {
            if (participantId != null && participantId.equals(p.getId())) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.ENTITY_EXISTS, "targetId",
                    "participant '" + participantId + "' does not exist in encounter "
                            + context.encounterId()));
        }
        return Optional.empty();
    }
}
