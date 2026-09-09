package com.gamemasterx.server.campaign.invite.model;

/**
 * Lifecycle status of a {@link CampaignInvite}.
 *
 * <p>An invite starts life as {@link #PENDING} once issued and moves to
 * {@link #USED} once it has been redeemed. The issuer may {@link #REVOKED} an
 * invite before it is used, and invites that pass their {@code expiresAt}
 * timestamp are treated as {@link #EXPIRED} when redeemed.</p>
 */
public enum InviteStatus {

    /**
     * The invite is live and may still be redeemed.
     */
    PENDING,

    /**
     * The invite has been redeemed and consumed.
     */
    USED,

    /**
     * The invite was revoked by its issuer before being redeemed.
     */
    REVOKED,

    /**
     * The invite passed its expiry timestamp and can no longer be redeemed.
     */
    EXPIRED
}
