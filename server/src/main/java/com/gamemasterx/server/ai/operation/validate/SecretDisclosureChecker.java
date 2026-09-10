package com.gamemasterx.server.ai.operation.validate;

import org.springframework.stereotype.Component;

import com.gamemasterx.server.ai.operation.OperationType;
import com.gamemasterx.server.ai.operation.ProposedOperation;
import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.campaign.membership.service.MembershipService;

import java.util.Optional;
import java.util.Set;

/**
 * Enforces the {@link ValidationDimension#SECRET_DISCLOSURE} dimension.
 *
 * <p>Some information in a campaign is restricted to the game master &ndash;
 * monster statistics the player does not know, plot secrets, hidden conditions,
 * and so on. A proposal must never be able to request the disclosure of such
 * secret information to an actor who is not entitled to receive it. This checker
 * rejects any proposal whose parameters ask to reveal, expose, or otherwise
 * disclose a restricted value unless the actor holds the {@link
 * MembershipRole#GAME_MASTER} role.</p>
 */
@Component
public class SecretDisclosureChecker implements OperationChecker {

    /**
     * Parameter names that, when present, request disclosure of restricted or
     * secret information.
     */
    private static final Set<String> SECRET_KEYS = Set.of(
            "secret", "secrets", "reveal", "revealed", "revealSecret",
            "hidden", "exposeSecret", "dmSecret", "dmSecrets", "monsterStats",
            "hiddenStats", "know");

    private final MembershipService membershipService;

    /**
     * @param membershipService the single backend authority on role-based access
     */
    public SecretDisclosureChecker(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Override
    public ValidationDimension dimension() {
        return ValidationDimension.SECRET_DISCLOSURE;
    }

    @Override
    public Optional<OperationValidationError> check(ProposedOperation proposal, ProposedOperationContext context) {
        if (proposal.operationType() == OperationType.NARRATE) {
            // Narrative context may reference the world, but must not be a route
            // to exfiltrate restricted secret values to an unauthorized actor.
        }
        String forbidden = forbiddenSecretKey(proposal.parameters());
        if (forbidden == null) {
            return Optional.empty();
        }
        try {
            membershipService.assertAuthorized(
                    context.campaignId(), context.actor(), MembershipRole.GAME_MASTER);
        } catch (RuntimeException notAuthorized) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.SECRET_DISCLOSURE, forbidden,
                    "the proposal requests disclosure of restricted information ('"
                            + forbidden + "') that may only be revealed by a game master"));
        }
        return Optional.empty();
    }

    private static String forbiddenSecretKey(java.util.Map<String, Object> parameters) {
        for (String key : parameters.keySet()) {
            if (key != null && SECRET_KEYS.contains(key.toLowerCase())) {
                return key;
            }
        }
        return null;
    }
}
