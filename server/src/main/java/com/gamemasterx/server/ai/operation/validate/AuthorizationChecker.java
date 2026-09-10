package com.gamemasterx.server.ai.operation.validate;

import org.springframework.stereotype.Component;
import com.gamemasterx.server.ai.operation.OperationType;
import com.gamemasterx.server.ai.operation.ProposedOperation;
import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.campaign.membership.service.MembershipService;
import com.gamemasterx.server.exception.AuthorizationException;

import java.util.Optional;

/**
 * Enforces the {@link ValidationDimension#AUTHORIZATION} dimension.
 *
 * <p>Authorization is enforced by the backend for every proposed operation: the
 * caller must hold the role this operation requires in the campaign it targets.
 * The required role is backend-declared here and never supplied by the caller.
 * Player-scoped state changes and movements require the {@link
 * MembershipRole#GAME_MASTER} role; narrative commentary only requires the
 * lowest {@link MembershipRole#OBSERVER} role.</p>
 */

@Component
public class AuthorizationChecker implements OperationChecker {

    private final MembershipService membershipService;

    /**
     * @param membershipService the single backend authority on role-based access
     */
    public AuthorizationChecker(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Override
    public ValidationDimension dimension() {
        return ValidationDimension.AUTHORIZATION;
    }

    @Override
    public Optional<OperationValidationError> check(ProposedOperation proposal, ProposedOperationContext context) {
        MembershipRole required = requiredRole(proposal.operationType());
        try {
            membershipService.assertAuthorized(context.campaignId(), context.actor(), required);
            return Optional.empty();
        } catch (AuthorizationException ex) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.AUTHORIZATION, "actor",
                    "you do not hold the " + required + " role required to "
                            + proposal.operationType().name().toLowerCase() + " ("
                            + ex.getMessage() + ")"));
        }
    }

    private static MembershipRole requiredRole(OperationType type) {
        // Only narrative proposals are permitted for the lowest role; every
        // state-changing operation is a game-master action.
        return (type == OperationType.NARRATE)
                ? MembershipRole.OBSERVER
                : MembershipRole.GAME_MASTER;
    }
}
