package com.gamemasterx.server.campaign.service;

import com.gamemasterx.server.campaign.model.Campaign;
import com.gamemasterx.server.campaign.model.CampaignCreateRequest;
import com.gamemasterx.server.campaign.model.CampaignDto;
import com.gamemasterx.server.campaign.model.CampaignStatus;
import com.gamemasterx.server.campaign.model.CampaignUpdateRequest;
import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.campaign.membership.service.MembershipService;
import com.gamemasterx.server.campaign.repository.CampaignRepository;
import com.gamemasterx.server.adventure.repository.AdventureRepository;
import com.gamemasterx.server.campaign.websocket.CampaignEventWebSocketHandler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CampaignService {

    /** Current schema version for the Campaign document shape. */
    private static final int SCHEMA_VERSION = 1;

    private final CampaignRepository campaignRepository;
    private final MembershipService membershipService;
    private final AdventureRepository adventureRepository;

    public CampaignService(CampaignRepository campaignRepository,
                           MembershipService membershipService,
                           AdventureRepository adventureRepository) {
        this.campaignRepository = campaignRepository;
        this.membershipService = membershipService;
        this.adventureRepository = adventureRepository;
    }

    /**
     * Creates a new Campaign aggregate. The creator becomes the campaign
     * {@link MembershipRole#OWNER}, which is recorded as an OWNER membership so
     * that later operations can be authorized against the role hierarchy.
     * Creating a campaign requires an authenticated caller.
     */
    public CampaignDto createCampaign(CampaignCreateRequest request, String creatorId) {
        String name = request.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Campaign name must not be empty");
        }
        String creator = requireCreator(creatorId);

        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Campaign campaign = new Campaign();
        campaign.setId(id);
        campaign.setSchemaVersion(SCHEMA_VERSION);
        campaign.setRevision(1);
        campaign.setCreatedAt(now);
        campaign.setUpdatedAt(now);
        campaign.setName(name);
        campaign.setDescription(request.getDescription());
        campaign.setGameSystem(request.getGameSystem());
        campaign.setStatus(request.getStatus() != null ? request.getStatus() : CampaignStatus.DRAFT);
        campaign.setMaxPlayers(request.getMaxPlayers());
        campaignRepository.save(campaign);
        // Establish ownership so subsequent operations are authorized.
        membershipService.grantOwner(id, creator);
        CampaignEventWebSocketHandler.broadcast(id, "campaign_created", "{\"id\":\"" + id + "\"}");
        return toDto(campaign);
    }

    /**
     * Finds a Campaign by its identifier for an authenticated caller. The caller
     * must hold at least the lowest membership role ({@link
     * MembershipRole#OBSERVER}) in the campaign, so that only members can read a
     * campaign.
     */
    public CampaignDto findById(String campaignId, String actor) {
        membershipService.assertAuthorized(campaignId, actor, MembershipRole.OBSERVER);
        return toDto(requireCampaign(campaignId));
    }

    /**
     * Lists Campaigns filtered by lifecycle status. With a {@code null} status
     * every campaign is returned.
     */
    public List<CampaignDto> findByStatus(CampaignStatus status) {
        List<Campaign> campaigns = (status == null)
                ? campaignRepository.findAll()
                : campaignRepository.findByStatus(status);
        return campaigns.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Updates a Campaign aggregate. Only non-null fields supplied in the request
     * are changed, allowing partial updates. The {@code revision} counter is
     * managed automatically by Spring Data MongoDB ({@link
     * org.springframework.data.annotation.Version}) for optimistic concurrency
     * control.
     */
    public CampaignDto updateCampaign(String id, String actor, CampaignUpdateRequest request) {
        membershipService.assertAuthorized(id, actor, MembershipRole.GAME_MASTER);
        Campaign existing = requireCampaign(id);

        if (request.getName() != null && !request.getName().isBlank()) {
            existing.setName(request.getName());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getGameSystem() != null) {
            existing.setGameSystem(request.getGameSystem());
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        if (request.getMaxPlayers() != null) {
            existing.setMaxPlayers(request.getMaxPlayers());
        }

        // Selecting (or clearing) the adventure must validate the target
        // adventure exists so the campaign never stores a dangling reference.
        if (request.getAdventureId() != null) {
            existing.setAdventureId(resolveAdventureId(request.getAdventureId()));
        }

        existing.setRevision(existing.getRevision() + 1);
        existing.setUpdatedAt(Instant.now());
        CampaignDto dto = toDto(campaignRepository.save(existing));
        CampaignEventWebSocketHandler.broadcast(id, "campaign_updated", "{\"id\":\"" + id + "\"}");
        return dto;
    }

    /**
     * Archives a Campaign by moving it to the {@link CampaignStatus#ARCHIVED}
     * state.
     */
    public CampaignDto archiveCampaign(String id, String actor) {
        membershipService.assertAuthorized(id, actor, MembershipRole.GAME_MASTER);
        Campaign existing = requireCampaign(id);
        existing.setStatus(CampaignStatus.ARCHIVED);
        existing.setRevision(existing.getRevision() + 1);
        existing.setUpdatedAt(Instant.now());
        CampaignDto dto = toDto(campaignRepository.save(existing));
        CampaignEventWebSocketHandler.broadcast(id, "campaign_archived", "{\"id\":\"" + id + "\"}");
        return dto;
    }

    private Campaign requireCampaign(String id) {
        return campaignRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Campaign not found: " + id));
    }

    /**
     * Confirms the supplied adventure identifier references a real authored
     * adventure, so a campaign never stores a dangling reference.
     *
     * @param adventureId the adventure identifier to resolve
     * @return the validated, non-blank adventure identifier
     * @throws IllegalArgumentException when the identifier is blank or no such
     *                                adventure exists
     */
    private String resolveAdventureId(String adventureId) {
        if (adventureId == null || adventureId.isBlank()) {
            return null;
        }
        if (adventureRepository.findById(adventureId).isEmpty()) {
            throw new IllegalArgumentException("Adventure not found: " + adventureId);
        }
        return adventureId;
    }

    private static String requireCreator(String creatorId) {
        if (creatorId == null || creatorId.isBlank()) {
            throw new com.gamemasterx.server.exception.AuthorizationException(
                    MembershipRole.OBSERVER, "Authentication required to create a campaign");
        }
        return creatorId;
    }

    /**
     * Projects a persisted document entity into the API-facing DTO. The
     * persistence-only revision counter is intentionally excluded from the API
     * representation.
     */
    private CampaignDto toDto(Campaign campaign) {
        return CampaignDto.from(campaign);
    }
}
