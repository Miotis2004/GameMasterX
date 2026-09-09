package com.gamemasterx.server.campaign.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a Campaign.
 *
 * <p>Separate from the {@link CampaignDto} API representation and the
 * {@link Campaign} persisted entity, this DTO is the server-side validation
 * boundary. Its annotations are enforced by Bean Validation before the request
 * reaches {@code CampaignService}.</p>
 */
public class CampaignCreateRequest {

    @NotBlank(message = "Campaign name must not be blank")
    @Size(max = 100, message = "Campaign name must not exceed 100 characters")
    private String name;

    @Size(max = 5000, message = "Campaign description must not exceed 5000 characters")
    private String description;

    @Size(max = 100, message = "Game system must not exceed 100 characters")
    private String gameSystem;

    private CampaignStatus status;

    @Min(value = 1, message = "Max players must be at least 1")
    @Max(value = 100, message = "Max players must not exceed 100")
    private int maxPlayers;

    public CampaignCreateRequest() {
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
}
