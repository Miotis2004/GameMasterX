package com.gamemasterx.server.encounter.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating a new {@link Encounter}.
 *
 * <p>Only the minimum identifying information is required on creation; the
 * participants, initiative, positions and other resolution state are built up
 * through the encounter service methods (for example by adding participants)
 * before the encounter is started. This keeps the creation contract small and
 * lets the game master organise the encounter in the {@link
 * EncounterStatus#DRAFT} state.</p>
 */
public class EncounterCreateRequest {

    /**
     * The wire representation of the {@link RulesProfile} the game master wants
     * the encounter to follow. May be {@code null} or blank, in which case the
     * service applies the default profile ({@code SRD-5.2-2024}).
     */
    private String rulesProfile;

    @NotBlank(message = "Campaign identifier must not be blank")
    private String campaignId;

    private String name;

    public EncounterCreateRequest() {
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRulesProfile() {
        return rulesProfile;
    }

    public void setRulesProfile(String rulesProfile) {
        this.rulesProfile = rulesProfile;
    }
}
