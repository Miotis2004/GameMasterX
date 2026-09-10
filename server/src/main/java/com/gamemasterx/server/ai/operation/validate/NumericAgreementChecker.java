package com.gamemasterx.server.ai.operation.validate;

import org.springframework.stereotype.Component;
import com.gamemasterx.server.ai.operation.OperationType;
import com.gamemasterx.server.ai.operation.ProposedOperation;
import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.gameplay.service.DamageService;

import java.util.Optional;

/**
 * Enforces the {@link ValidationDimension#NUMERIC_AGREEMENT} dimension.
 *
 * <p>The numeric values carried on the proposal must agree with each other and
 * with the deterministic resolution performed by the backend. For damage and
 * healing the declared {@code amount} must be a non-negative integer, and it
 * must resolve through the backend-owned {@link DamageService} to hit points
 * that remain within the legal {@code [0, max]} range. Any inconsistency &ndash;
 * a missing, non-integer, or negative amount, or a proposal that disagrees with
 * the stored hit-point state &ndash; is reported here.</p>
 */

@Component
public class NumericAgreementChecker implements OperationChecker {

    private final DamageService damageService;

    /**
     * @param damageService the single authority for hit-point resolution
     */
    public NumericAgreementChecker(DamageService damageService) {
        this.damageService = damageService;
    }

    @Override
    public ValidationDimension dimension() {
        return ValidationDimension.NUMERIC_AGREEMENT;
    }

    @Override
    public Optional<OperationValidationError> check(ProposedOperation proposal, ProposedOperationContext context) {
        OperationType type = proposal.operationType();
        if (type != OperationType.APPLY_DAMAGE && type != OperationType.APPLY_HEALING) {
            return Optional.empty();
        }
        Encounter encounter = context.getEncounter();
        if (encounter == null) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.NUMERIC_AGREEMENT, "targetId",
                    "the target encounter must be loaded to verify numeric agreement"));
        }
        Encounter.Participant participant = findParticipant(encounter, proposal.targetId());
        if (participant == null || participant.getHitPoints() == null) {
            return Optional.empty();
        }
        Integer amount = integerParameter(proposal, "amount");
        if (amount == null) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.NUMERIC_AGREEMENT, "amount",
                    "a " + type + " must carry an integer 'amount'"));
        }
        if (amount < 0) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.NUMERIC_AGREEMENT, "amount",
                    "the " + type + " amount must not be negative (was " + amount + ")"));
        }
        try {
            if (type == OperationType.APPLY_DAMAGE) {
                damageService.resolveDamage(
                        new DamageService.DamageInput(participant.getHitPoints(), amount));
            } else {
                damageService.resolveHealing(
                        new DamageService.HealingInput(participant.getHitPoints(), amount));
            }
            return Optional.empty();
        } catch (IllegalArgumentException ex) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.NUMERIC_AGREEMENT, "amount",
                    "the proposed " + type + " does not agree with the stored hit-point state ("
                            + ex.getMessage() + ")"));
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
