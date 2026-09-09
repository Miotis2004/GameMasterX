package com.gamemasterx.server.campaign.membership.model;

import com.gamemasterx.server.campaign.membership.model.MembershipStatus;
import java.time.Instant;

/**
 * API-facing representation of a {@link Membership}.
 *
 * <p>This DTO is intentionally distinct from the persisted {@link Membership}
 * MongoDB document entity. It defines only the fields that are safe to expose
 * over the wire and carries no persistence metadata such as the
 * optimistic-concurrency {@code revision} counter.</p>
 */
public class MembershipDto {

    private String id;
    private int schemaVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private String userId;
    private String campaignId;
    private MembershipRole role;
    private MembershipStatus status;

    public MembershipDto() {
    }

    /**
     * Builds the API representation from a persisted document entity.
     *
     * @param membership the membership to project
     * @return the API-facing DTO
     */
    public static MembershipDto from(Membership membership) {
        MembershipDto dto = new MembershipDto();
        dto.id = membership.getId();
        dto.schemaVersion = membership.getSchemaVersion();
        dto.createdAt = membership.getCreatedAt();
        dto.updatedAt = membership.getUpdatedAt();
        dto.userId = membership.getUserId();
        dto.campaignId = membership.getCampaignId();
        dto.role = membership.getRole();
        dto.status = membership.getStatus();
        return dto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public MembershipRole getRole() {
        return role;
    }

    public void setRole(MembershipRole role) {
        this.role = role;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipStatus status) {
        this.status = status;
    }
}
