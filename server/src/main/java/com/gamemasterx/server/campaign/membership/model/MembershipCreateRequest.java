package com.gamemasterx.server.campaign.membership.model;

/**
 * Request body for adding a user to a campaign with a specific role.
 */
public class MembershipCreateRequest {

    /**
     * Stable identifier of the user to add.
     */
    private String userId;

    /**
     * Desired role. Accepts any {@link MembershipRole} name (case-insensitive),
     * for example {@code "GAME_MASTER"}.
     */
    private String role;

    public MembershipCreateRequest() {
    }

    public MembershipCreateRequest(String userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
