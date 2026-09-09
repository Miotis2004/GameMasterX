package com.gamemasterx.server.campaign.membership.model;

/**
 * Lifecycle status of a {@link Membership}.
 *
 * <p>A membership starts life as either {@link #ACTIVE} when it is granted
 * directly through an issuer with authority (for example an invite issued by a
 * campaign owner or game master), or {@link #PENDING} when it is created
 * through an open join code that awaits approval before the joiner is fully
 * admitted to the campaign.</p>
 */
public enum MembershipStatus {

    /**
     * The member is fully admitted and participates in the campaign.
     */
    ACTIVE,

    /**
     * The member has redeemed a join code but awaits approval before becoming
     * active.
     */
    PENDING,

    /**
     * The membership has been revoked and the member no longer participates.
     */
    REVOKED
}
