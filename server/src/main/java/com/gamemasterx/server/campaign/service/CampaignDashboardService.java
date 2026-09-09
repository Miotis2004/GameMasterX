package com.gamemasterx.server.campaign.service;

import com.gamemasterx.server.adventure.model.Adventure;
import com.gamemasterx.server.adventure.model.AdventureDto;
import com.gamemasterx.server.adventure.repository.AdventureRepository;
import com.gamemasterx.server.campaign.model.Campaign;
import com.gamemasterx.server.campaign.model.CampaignDashboardDto;
import com.gamemasterx.server.campaign.model.CampaignDto;
import com.gamemasterx.server.campaign.membership.model.MembershipDto;
import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.campaign.membership.service.MembershipService;
import com.gamemasterx.server.campaign.repository.CampaignRepository;
import com.gamemasterx.server.character.model.CharacterDto;
import com.gamemasterx.server.character.repository.CharacterRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Application service for the campaign dashboard.
 *
 * <p>The dashboard aggregates, for a single campaign, the three views a game
 * master needs to open a session: the campaign's members and their roles, the
 * campaign's characters, and the campaign's selected adventure. All three
 * aggregates are resolved in one call so the UI does not need to issue three
 * sequentially dependent requests.</p>
 *
 * <p>As with every other campaign operation, reading the dashboard is authorized
 * against the caller's {@link MembershipRole} in the campaign. The minimum role
 * is {@link MembershipRole#OBSERVER} (membership), matching the access rules of
 * the campaign and membership read endpoints.</p>
 */
@Service
public class CampaignDashboardService {

    private final CampaignRepository campaignRepository;
    private final MembershipService membershipService;
    private final CharacterRepository characterRepository;
    private final AdventureRepository adventureRepository;

    public CampaignDashboardService(CampaignRepository campaignRepository,
                                    MembershipService membershipService,
                                    CharacterRepository characterRepository,
                                    AdventureRepository adventureRepository) {
        this.campaignRepository = campaignRepository;
        this.membershipService = membershipService;
        this.characterRepository = characterRepository;
        this.adventureRepository = adventureRepository;
    }

    /**
     * Builds the dashboard for a campaign for the given authenticated caller.
     *
     * <p>The caller must hold at least the lowest membership role
     * ({@link MembershipRole#OBSERVER}) in the campaign, so only members can read
     * the dashboard. The returned view contains the campaign, the caller's role,
     * every member with their role and status, every character associated with
     * the campaign, and the selected adventure when one has been chosen.</p>
     *
     * @param campaignId stable campaign identifier
     * @param actor      stable user identifier of the authenticated caller
     * @return the assembled campaign dashboard
     * @throws IllegalArgumentException when the campaign does not exist
     * @throws com.gamemasterx.server.exception.AuthorizationException when the
     *         caller is not a member of the campaign
     */
    public CampaignDashboardDto getCampaignDashboard(String campaignId, String actor) {
        membershipService.assertAuthorized(campaignId, actor, MembershipRole.OBSERVER);

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));
        CampaignDto campaignDto = CampaignDto.from(campaign);

        MembershipRole actorRole = membershipService.getRoleForUser(campaignId, actor);

        List<MembershipDto> members = membershipService.listMembers(campaignId);
        List<CharacterDto> characters = characterRepository.findByCampaignId(campaignId)
                .stream()
                .map(this::toCharacterDto)
                .collect(Collectors.toList());
        AdventureDto selectedAdventure = resolveSelectedAdventure(campaign.getAdventureId());

        return CampaignDashboardDto.of(
                campaignDto, actor, actorRole, members, characters, selectedAdventure);
    }

    private AdventureDto resolveSelectedAdventure(String adventureId) {
        if (adventureId == null || adventureId.isBlank()) {
            return null;
        }
        Optional<Adventure> adventure = adventureRepository.findById(adventureId);
        return adventure.map(this::toAdventureDto).orElse(null);
    }

    private CharacterDto toCharacterDto(com.gamemasterx.server.character.model.Character character) {
        return CharacterDto.from(character);
    }

    private AdventureDto toAdventureDto(Adventure adventure) {
        return AdventureDto.from(adventure);
    }
}
