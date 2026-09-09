package com.gamemasterx.server.campaign.model;

import java.time.Instant;

/**
 * API-facing representation of a Campaign.
 *
 * <p>This DTO is intentionally distinct from the persisted {@link Campaign}
 * MongoDB document entity. It defines only the fields that are safe to expose
 * over the wire and carries no persistence metadata such as the
 * optimistic-concurrency {@code revision} counter.</p>
 *
 * <p>It is used both as the shape returned by the campaign endpoints and as the
 * projection of a {@link Campaign} document into the API contract.</p>
 */
public class CampaignDto {

    private String id;
    private int schemaVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private String name;
    private String description;
    private String gameSystem;
    private CampaignStatus status;
    private int maxPlayers;
    /**
     * Stable identifier of the adventure this campaign has selected, or
     * {@code null} when no adventure has been selected.
     */
    private String adventureId;

    public CampaignDto() {
    }

    /**
     * Builds the API representation from a persisted document entity.
     */
    public static CampaignDto from(Campaign campaign) {
        CampaignDto dto = new CampaignDto();
        dto.id = campaign.getId();
        dto.schemaVersion = campaign.getSchemaVersion();
        dto.createdAt = campaign.getCreatedAt();
        dto.updatedAt = campaign.getUpdatedAt();
        dto.name = campaign.getName();
        dto.description = campaign.getDescription();
        dto.gameSystem = campaign.getGameSystem();
        dto.status = campaign.getStatus();
        dto.maxPlayers = campaign.getMaxPlayers();
        dto.adventureId = campaign.getAdventureId();
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGameSystem() {
        return gameSystem;
    }

    public void setGameSystem(String gameSystem) {
        this.gameSystem = gameSystem;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public void setStatus(CampaignStatus status) {
        this.status = status;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public String getAdventureId() {
        return adventureId;
    }

    public void setAdventureId(String adventureId) {
        this.adventureId = adventureId;
    }
}
