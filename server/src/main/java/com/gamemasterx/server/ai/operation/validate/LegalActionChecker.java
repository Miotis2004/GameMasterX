package com.gamemasterx.server.ai.operation.validate;

import org.springframework.stereotype.Component;
import com.gamemasterx.server.ai.operation.OperationType;
import com.gamemasterx.server.ai.operation.ProposedOperation;
import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.encounter.model.EncounterStatus;
import com.gamemasterx.server.encounter.RulesProfileValidationException;
import com.gamemasterx.server.encounter.service.RulesProfileService;

import java.util.Optional;

/**
 * Enforces the {@link ValidationDimension#LEGAL_ACTION} dimension.
 *
 * <p>Legal-action is decided by the deterministic backend rules engine, never by
 * the caller. For a participant-scoped operation this requires the encounter to
 * be {@link EncounterStatus#ACTIVE} (no state change may be applied in draft,
 * paused or completed play) and the encounter's selected, supported rules
 * profile to permit the encounter to proceed. {@link OperationType#NARRATE}
 * adds narrative context and imposes no state requirement.</p>
 */

@Component
public class LegalActionChecker implements OperationChecker {

    private final RulesProfileService rulesProfileService;

    /**
     * @param rulesProfileService the single authority for the rules subset
     */
    public LegalActionChecker(RulesProfileService rulesProfileService) {
        this.rulesProfileService = rulesProfileService;
    }

    @Override
    public ValidationDimension dimension() {
        return ValidationDimension.LEGAL_ACTION;
    }

    @Override
    public Optional<OperationValidationError> check(ProposedOperation proposal, ProposedOperationContext context) {
        if (proposal.operationType() == OperationType.NARRATE) {
            return Optional.empty();
        }
        Encounter encounter = context.getEncounter();
        if (encounter == null) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.LEGAL_ACTION, "encounterId",
                    "the target encounter must be loaded to verify the action is legal"));
        }
        if (encounter.getStatus() != EncounterStatus.ACTIVE) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.LEGAL_ACTION, null,
                    "a " + proposal.operationType() + " can only be applied while the encounter is active "
                            + "(current state: " + encounter.getStatus() + ")"));
        }
        try {
            rulesProfileService.validateEncounterCanProceed(encounter);
            return Optional.empty();
        } catch (RulesProfileValidationException ex) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.LEGAL_ACTION, null, ex.getMessage()));
        }
    }
}
