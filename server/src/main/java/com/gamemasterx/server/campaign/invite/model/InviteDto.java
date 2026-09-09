package com.gamemasterx.server.campaign.invite.model;

import com.gamemasterx.server.campaign.membership.model.MembershipRole;

import java.time.Instant;

/**
 * API-facing representation of a {@link CampaignInvite}.
 *
 * <p>Exposes only the fields safe to send over the wire. The secret
 * {@link #inviteCode} is intentionally included because callers need it to
 * redeem the invite.</p>
 */
public class InviteDto {

    private String id;
    private String inviteCode;
    private String campaignId;
    private String issuedBy;
    private MembershipRole role;
    private InviteRedeemMode redeemMode;
    private InviteStatus status;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;

    public InviteDto() {
    }

    /**
     * Builds the API representation from a persisted document entity.
     */
    public static InviteDto from(CampaignInvite invite) {
        InviteDto dto = new InviteDto();
        dto.id = invite.getId();
        dto.inviteCode = invite.getInviteCode();
        dto.campaignId = invite.getCampaignId();
        dto.issuedBy = invite.getIssuedBy();
        dto.role = invite.getRole();
        dto.redeemMode = invite.getRedeemMode();
        dto.status = invite.getStatus();
        dto.expiresAt = invite.getExpiresAt();
        dto.createdAt = invite.getCreatedAt();
        dto.updatedAt = invite.getUpdatedAt();
        return dto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(String issuedBy) {
        this.issuedBy = issuedBy;
    }

    public MembershipRole getRole() {
        return role;
    }

    public void setRole(MembershipRole role) {
        this.role = role;
    }

    public InviteRedeemMode getRedeemMode() {
        return redeemMode;
    }

    public void setRedeemMode(InviteRedeemMode redeemMode) {
        this.redeemMode = redeemMode;
    }

    public InviteStatus getStatus() {
        return status;
    }

    public void setStatus(InviteStatus status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
