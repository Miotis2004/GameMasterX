package com.gamemasterx.server.campaign.model;

import com.gamemasterx.server.adventure.model.AdventureDto;
import com.gamemasterx.server.character.model.CharacterDto;
import com.gamemasterx.server.campaign.membership.model.MembershipDto;
import com.gamemasterx.server.campaign.membership.model.MembershipRole;

import java.util.ArrayList;
import java.util.List;

/**
 * API-facing representation of a campaign dashboard.
 *
 * <p>The dashboard is a single aggregation endpoint that brings together, for a
 * single campaign, the three views a game master needs to open a session: the
 * campaign's <em>members and their roles</em>, the campaign's <em>characters</em>,
 * and the campaign's <em>selected adventure</em>. Keeping these together avoids
 * the client having to issue three separate, sequentially dependent requests.</p>
 *
 * <p>This DTO is intentionally distinct from the persisted aggregates. It
 * exposes only the fields that are safe to over the wire and carries the
 * caller's own role ({@link #actorRole}) so the UI can render
 * role-aware controls without issuing a separate authorization request.</p>
 */
public class CampaignDashboardDto {

    /** The campaign the dashboard describes. */
    private CampaignDto campaign;

    /** Stable identifier of the authenticated caller who requested the dashboard. */
    private String actorId;

    /** The role the {@link #actorId} holds in the campaign. */
    private MembershipRole actorRole;

    /** The campaign's members with their roles and status. */
    private List<MembershipDto> members;

    /** The characters associated with the campaign. */
    private List<CharacterDto> characters;

    /** The adventure the campaign has selected, or {@code null} when none is selected. */
    private AdventureDto selectedAdventure;

    public CampaignDashboardDto() {
        this.members = new ArrayList<>();
        this.characters = new ArrayList<>();
    }

    /**
     * Builds a dashboard view of a campaign.
     *
     * @param campaign      the campaign the caller is authorized to view
     * @param actorId       the authenticated caller's identifier, or {@code null}
     * @param actorRole     the caller's role in the campaign (may be
     *                      {@link MembershipRole#OBSERVER} when unknown)
     * @param members       the campaign's members with their roles
     * @param characters    the characters associated with the campaign
     * @param selectedAdventure the selected adventure, or {@code null}
     * @return the assembled dashboard
     */
    public static CampaignDashboardDto of(CampaignDto campaign,
                                          String actorId,
                                          MembershipRole actorRole,
                                          List<MembershipDto> members,
                                          List<CharacterDto> characters,
                                          AdventureDto selectedAdventure) {
        CampaignDashboardDto dashboard = new CampaignDashboardDto();
        dashboard.campaign = campaign;
        dashboard.actorId = actorId;
        dashboard.actorRole = actorRole;
        dashboard.members = (members != null) ? members : new ArrayList<>();
        dashboard.characters = (characters != null) ? characters : new ArrayList<>();
        dashboard.selectedAdventure = selectedAdventure;
        return dashboard;
    }

    public CampaignDto getCampaign() {
        return campaign;
    }

    public void setCampaign(CampaignDto campaign) {
        this.campaign = campaign;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public MembershipRole getActorRole() {
        return actorRole;
    }

    public void setActorRole(MembershipRole actorRole) {
        this.actorRole = actorRole;
    }

    public List<MembershipDto> getMembers() {
        return members;
    }

    public void setMembers(List<MembershipDto> members) {
        this.members = (members != null) ? members : new ArrayList<>();
    }

    public List<CharacterDto> getCharacters() {
        return characters;
    }

    public void setCharacters(List<CharacterDto> characters) {
        this.characters = (characters != null) ? characters : new ArrayList<>();
    }

    public AdventureDto getSelectedAdventure() {
        return selectedAdventure;
    }

    public void setSelectedAdventure(AdventureDto selectedAdventure) {
        this.selectedAdventure = selectedAdventure;
    }
}
