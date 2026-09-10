package com.gamemasterx.server.ai.operation.validate;

import org.springframework.stereotype.Component;
import com.gamemasterx.server.ai.operation.OperationType;
import com.gamemasterx.server.ai.operation.ProposedOperation;
import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.campaign.membership.service.MembershipService;
import com.gamemasterx.server.encounter.model.ActorControl;
import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.exception.AuthorizationException;

import java.util.Optional;

/**
 * Enforces the {@link ValidationDimension#ACTOR_CONTROL} dimension.
 *
 * <p>Actor control decides who may issue actions on behalf of a participant. A
 * {@link ActorControl#SELF} participant (a player's own character) may be acted
 * for by any member, but a {@link ActorControl#GM} or {@link
 * ActorControl#AUTOMATED} participant (an NPC or monster) may only be acted for
 * by an actor holding the {@link MembershipRole#GAME_MASTER} role. This keeps
 * the AI from issuing operations on behalf of participants the caller does not
 * control.</p>
 */

@Component
public class ActorControlChecker implements OperationChecker {

    private final MembershipService membershipService;

    /**
     * @param membershipService the single backend authority on role-based access
     */
    public ActorControlChecker(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Override
    public ValidationDimension dimension() {
        return ValidationDimension.ACTOR_CONTROL;
    }

    @Override
    public Optional<OperationValidationError> check(ProposedOperation proposal, ProposedOperationContext context) {
        if (proposal.operationType() == OperationType.NARRATE) {
            // Narrative context does not act on behalf of a participant.
            return Optional.empty();
        }
        Encounter encounter = context.getEncounter();
        if (encounter == null) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.ACTOR_CONTROL, "targetId",
                    "the target encounter must be loaded to verify actor control"));
        }

        Encounter.Participant participant = findParticipant(encounter, proposal.targetId());
        if (participant == null) {
            // Existence is reported by the entity-existence dimension.
            return Optional.empty();
        }

        ActorControl control = participant.getActorControl();
        if (control == ActorControl.GM || control == ActorControl.AUTOMATED) {
            try {
                membershipService.assertAuthorized(
                        context.campaignId(), context.actor(), MembershipRole.GAME_MASTER);
            } catch (AuthorizationException ex) {
                return Optional.of(new OperationValidationError(
                        ValidationDimension.ACTOR_CONTROL, "targetId",
                        "you may not act on behalf of a " + control + " participant ("
                                + ex.getMessage() + ")"));
            }
        }
        return Optional.empty();
    }

    private static Encounter.Participant findParticipant(Encounter encounter, String participantId) {
        for (Encounter.Participant p : encounter.getParticipants()) {
            if (participantId != null && participantId.equals(p.getId())) {
                return p;
            }
        }
        return null;
    }
}
