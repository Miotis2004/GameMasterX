package com.gamemasterx.server.ai.operation.validate;

import org.springframework.stereotype.Component;
import com.gamemasterx.server.ai.operation.OperationType;
import com.gamemasterx.server.ai.operation.ProposedOperation;
import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.encounter.repository.EncounterRepository;

import java.util.Optional;

/**
 * Enforces the {@link ValidationDimension#EXPECTED_REVISION} dimension.
 *
 * <p>The caller may supply the revision it expects to observe. When supplied,
 * the stored revision must match before the backend executes the operation, so
 * a concurrent write that has already advanced the revision is detected and
 * rejected before any deterministic execution. The authoritative
 * optimistic-concurrency enforcement also runs inside the guarded commit; this
 * pre-check rejects an already-stale proposal early. A {@code null} expected
 * revision disables the check.</p>
 */

@Component
public class ExpectedRevisionChecker implements OperationChecker {

    private final EncounterRepository encounterRepository;

    /**
     * @param encounterRepository the authoritative store of encounters
     */
    public ExpectedRevisionChecker(EncounterRepository encounterRepository) {
        this.encounterRepository = encounterRepository;
    }

    @Override
    public ValidationDimension dimension() {
        return ValidationDimension.EXPECTED_REVISION;
    }

    @Override
    public Optional<OperationValidationError> check(ProposedOperation proposal, ProposedOperationContext context) {
        Long expected = context.expectedRevision();
        if (expected == null || proposal.operationType() == OperationType.NARRATE) {
            return Optional.empty();
        }
        if (context.encounterId() == null || context.encounterId().isBlank()) {
            return Optional.empty();
        }
        Encounter stored = encounterRepository.findById(context.encounterId()).orElse(null);
        if (stored == null) {
            // Entity existence is reported by the entity-existence dimension.
            return Optional.empty();
        }
        if (stored.getRevision() != expected.intValue()) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.EXPECTED_REVISION, "expectedRevision",
                    "the encounter has been updated since you last read it: expected revision "
                            + expected + " but stored revision is " + stored.getRevision()
                            + "; the operation was rejected to avoid a lost update"));
        }
        return Optional.empty();
    }
}
