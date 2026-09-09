package com.gamemasterx.server.exception;

import com.gamemasterx.server.campaign.membership.model.MembershipRole;

/**
 * Raised when an authenticated actor attempts an operation for which they do
 * not hold the required {@link MembershipRole} in the relevant campaign.
 *
 * <p>This is deliberately distinct from {@link IllegalArgumentException} so that
 * the global exception handler can map authorization denials to a consistent
 * {@code 403 Forbidden} response (see
 * {@link GlobalExceptionHandler}), while genuine "resource not found" or
 * "invalid input" problems continue to map to their existing responses.</p>
 *
 * <p>The {@link #getRequiredRole()} value is the minimum role the action
 * demands, as declared by the protected operation.</p>
 */
public class AuthorizationException extends RuntimeException {

    private final MembershipRole requiredRole;

    /**
     * Creates an authorization denial for the given required role.
     *
     * @param requiredRole the minimum role required to perform the action
     */
    public AuthorizationException(MembershipRole requiredRole) {
        this(requiredRole, "You do not have permission to perform this action");
    }

    /**
     * Creates an authorization denial for the given required role with a
     * caller-supplied message (for example {@code "Authentication required"}).
     *
     * @param requiredRole the minimum role required to perform the action
     * @param message      a human-readable explanation of the denial
     */
    public AuthorizationException(MembershipRole requiredRole, String message) {
        super(message);
        this.requiredRole = requiredRole;
    }

    /**
     * @return the minimum {@link MembershipRole} the denied action required, or
     * {@code null} when no specific role was recorded
     */
    public MembershipRole getRequiredRole() {
        return requiredRole;
    }
}
