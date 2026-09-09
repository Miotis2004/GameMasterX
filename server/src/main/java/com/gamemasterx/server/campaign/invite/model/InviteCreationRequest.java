package com.gamemasterx.server.campaign.invite.model;

/**
 * Request body for issuing an invite or generating a local join code for a
 * campaign.
 */
public class InviteCreationRequest {

    /**
     * The role the joiner will receive upon redemption. Accepts any
     * {@link com.gamemasterx.server.campaign.membership.model.MembershipRole}
     * name (case-insensitive), for example {@code "PLAYER"} or
     * {@code "GAME_MASTER"}.
     */
    private String role;

    /**
     * Optional lifetime, in seconds, of the issued invite or join code. When
     * omitted or zero, the invite never expires. Must be positive when
     * supplied.
     */
    private Long expiresAfterSeconds;

    public InviteCreationRequest() {
    }

    public InviteCreationRequest(String role, Long expiresAfterSeconds) {
        this.role = role;
        this.expiresAfterSeconds = expiresAfterSeconds;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getExpiresAfterSeconds() {
        return expiresAfterSeconds;
    }

    public void setExpiresAfterSeconds(Long expiresAfterSeconds) {
        this.expiresAfterSeconds = expiresAfterSeconds;
    }
}
