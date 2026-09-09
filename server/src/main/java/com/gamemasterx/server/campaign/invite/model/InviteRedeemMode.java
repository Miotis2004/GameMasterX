package com.gamemasterx.server.campaign.invite.model;

/**
 * Describes how redeeming a {@link CampaignInvite} affects the joiner's
 * membership within the campaign.
 *
 * <ul>
 *     <li>{@link #DIRECT} - redemption creates an {@link
 *     com.gamemasterx.server.campaign.membership.model.MembershipStatus#ACTIVE}
 *     membership. Used by targeted invites issued by an owner or game master.</li>
 *     <li>{@link #PENDING} - redemption creates a {@link
 *     com.gamemasterx.server.campaign.membership.model.MembershipStatus#PENDING}
 *     membership that awaits approval before the joiner is fully admitted. Used
 *     by local join codes.</li>
 * </ul>
 */
public enum InviteRedeemMode {

    DIRECT,
    PENDING
}
