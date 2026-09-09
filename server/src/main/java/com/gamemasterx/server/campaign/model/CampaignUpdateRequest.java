package com.gamemasterx.server.campaign.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request payload for editing an existing Campaign.
 *
 * <p>Unlike {@link CampaignCreateRequest}, every field is optional here so that
 * callers can perform a partial update. Bean Validation ignores {@code null}
 * values, so an omitted field is simply left unchanged; a provided field is
 * still validated (for example a {@code maxPlayers} of {@code 0} is rejected).</p>
 */
public class CampaignUpdateRequest {

    @Size(max = 100, message = "Campaign name must not exceed 100 characters")
    private String name;

    @Size(max = 5000, message = "Campaign description must not exceed 5000 characters")
    private String description;

    @Size(max = 100, message = "Game system must not exceed 100 characters")
    private String gameSystem;

    private CampaignStatus status;

    @Min(value = 1, message = "Max players must be at least 1")
    @Max(value = 100, message = "Max players must not exceed 100")
    private Integer maxPlayers;

    /**
     * Optional reference to the authored adventure this campaign selects. A
     * blank or {@code null} value clears the selection. Only campaigns that
     * already exist can have an adventure selected through this request; the
     * target adventure is validated for existence when the update is applied.
     */
    private String adventureId;

    public CampaignUpdateRequest() {
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

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public String getAdventureId() {
        return adventureId;
    }

    public void setAdventureId(String adventureId) {
        this.adventureId = adventureId;
    }
}
