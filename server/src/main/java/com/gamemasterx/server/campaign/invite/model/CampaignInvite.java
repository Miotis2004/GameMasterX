package com.gamemasterx.server.campaign.invite.model;

import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * Campaign invite aggregate root persisted as a MongoDB document.
 *
 * <p>An invite links a single-use, secret {@link #inviteCode} to a specific
 * {@link #campaignId} and {@link #role}. It is the unit through which a
 * campaign owner or game master ({@code issueInvite}) grants another user the
 * ability to join a campaign, and through which a local join code
 * ({@code generateJoinCode}) is created and later redeemed.</p>
 *
 * <p>The document is deliberately distinct from {@link InviteDto}: the entity is
 * what Spring Data MongoDB reads from and writes to the {@code campaign_invites}
 * collection, while the DTO is the API-facing representation.</p>
 *
 * <p>Exactly one invite exists per {@code inviteCode}, enforced by the unique
 * index below. An invite is scoped to a campaign and role: the role granted to
 * a joiner is fixed at issue time and cannot change.</p>
 */
@Document(collection = "campaign_invites")
@CompoundIndex(def = "inviteCode", unique = true, name = "idx_invite_code")
public class CampaignInvite {

    @Id
    private String id;

    /**
     * Secret, single-use code presented when issuing or redeeming an invite.
     * Unique across all invites.
     */
    @Indexed(unique = true)
    private String inviteCode;

    /**
     * Stable identifier of the campaign the invite grants access to.
     */
    @Indexed
    private String campaignId;

    /**
     * Identifier of the user who issued the invite. For local join codes this
     * is the user who generated the code; it may be null when the code was
     * generated without an authenticated issuer.
     */
    private String issuedBy;

    /**
     * The role granted to whoever redeems this invite. Fixed at issue time.
     */
    private MembershipRole role;

    /**
     * Controls whether redemption creates an active (direct) membership or a
     * pending membership awaiting approval.
     */
    private InviteRedeemMode redeemMode;

    private InviteStatus status;

    private Instant expiresAt;

    private Instant createdAt;

    private Instant updatedAt;

    public CampaignInvite() {
    }

    public CampaignInvite(String id, String inviteCode, String campaignId, String issuedBy,
                          MembershipRole role, InviteRedeemMode redeemMode, InviteStatus status,
                          Instant expiresAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.inviteCode = inviteCode;
        this.campaignId = campaignId;
        this.issuedBy = issuedBy;
        this.role = role;
        this.redeemMode = redeemMode;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    /**
     * @return {@code true} when this invite has a non-null {@code expiresAt}
     * that is in the past
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CampaignInvite that = (CampaignInvite) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
