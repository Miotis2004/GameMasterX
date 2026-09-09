package com.gamemasterx.server.campaign.invite.model;

/**
 * Request body for redeeming a {@link CampaignInvite} or local join code by
 * its code.
 */
public class RedeemInviteRequest {

    /**
     * Stable identifier of the user redeeming the invite or join code. This is
     * the person joining the campaign.
     */
    private String userId;

    public RedeemInviteRequest() {
    }

    public RedeemInviteRequest(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
