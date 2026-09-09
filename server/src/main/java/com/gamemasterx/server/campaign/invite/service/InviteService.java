package com.gamemasterx.server.campaign.invite.service;

import com.gamemasterx.server.campaign.invite.model.CampaignInvite;
import com.gamemasterx.server.campaign.invite.model.InviteCreationRequest;
import com.gamemasterx.server.campaign.invite.model.InviteDto;
import com.gamemasterx.server.campaign.invite.model.InviteRedeemMode;
import com.gamemasterx.server.campaign.invite.model.InviteStatus;
import com.gamemasterx.server.campaign.invite.repository.InviteRepository;
import com.gamemasterx.server.campaign.membership.model.MembershipDto;
import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.campaign.membership.service.MembershipService;
import com.gamemasterx.server.campaign.repository.CampaignRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Application service for campaign invites and local join codes.
 *
 * <p>This service owns the full lifecycle of {@link CampaignInvite} documents:
 * issuing targeted invites ({@link #issueInvite}) and generating local join
 * codes ({@link #generateJoinCode}), both of which are scoped to a specific
 * campaign and role, and redeeming them ({@link #redeem}) to create either an
 * active or a pending membership.</p>
 *
 * <p>Issuing is restricted to users who hold at least the
 * {@link MembershipRole#GAME_MASTER} role in the target campaign, following the
 * role hierarchy defined by {@link MembershipRole}. Local join codes and
 * invites share the same backing document; they differ only in their
 * {@link InviteRedeemMode}: invites grant an active membership directly, while
 * join codes create a pending membership awaiting approval.</p>
 */
@Service
public class InviteService {

    /** Current schema version for the CampaignInvite document shape. */
    private static final int SCHEMA_VERSION = 1;

    /**
     * Default maximum lifetime for a local join code when none is supplied.
     * Join codes are valid for 24 hours by default.
     */
    private static final long DEFAULT_JOIN_CODE_TTL_SECONDS = 24L * 60 * 60;

    private final InviteRepository inviteRepository;
    private final MembershipService membershipService;
    private final CampaignRepository campaignRepository;

    public InviteService(InviteRepository inviteRepository,
                         MembershipService membershipService,
                         CampaignRepository campaignRepository) {
        this.inviteRepository = inviteRepository;
        this.membershipService = membershipService;
        this.campaignRepository = campaignRepository;
    }

    /**
     * Issues a targeted invite for a campaign. The issuer must hold at least
     * the {@link MembershipRole#GAME_MASTER} role in the campaign. Redeeming the
     * invite creates an {@link MembershipStatus#ACTIVE} membership with the
     * granted role.
     *
     * @param campaignId   stable campaign identifier
     * @param issuerUserId stable identifier of the issuing owner or game master
     * @param request      the granted role and optional lifetime
     * @return the issued invite, projected into the API representation
     */
    public InviteDto issueInvite(String campaignId, String issuerUserId, InviteCreationRequest request) {
        requireCampaign(campaignId);
        String issuer = requireIssuer(issuerUserId, campaignId, MembershipRole.GAME_MASTER);
        MembershipRole role = parseRole(request.getRole());

        Instant now = Instant.now();
        Instant expiresAt = resolveExpiry(request.getExpiresAfterSeconds(), now);

        CampaignInvite invite = new CampaignInvite();
        invite.setId(UUID.randomUUID().toString());
        invite.setInviteCode(uniqueCode());
        invite.setCampaignId(campaignId);
        invite.setIssuedBy(issuer);
        invite.setRole(role);
        invite.setRedeemMode(InviteRedeemMode.DIRECT);
        invite.setStatus(InviteStatus.PENDING);
        invite.setExpiresAt(expiresAt);
        invite.setCreatedAt(now);
        invite.setUpdatedAt(now);

        return toDto(inviteRepository.save(invite));
    }

    /**
     * Generates a local join code for a campaign. The generator must hold at
     * least the {@link MembershipRole#GAME_MASTER} role in the campaign.
     * Redeeming the join code creates a {@link MembershipStatus#PENDING}
     * membership that awaits approval before the joiner is fully admitted.
     *
     * @param campaignId   stable campaign identifier
     * @param issuerUserId stable identifier of the generating owner or game master
     * @param request      the granted role and optional lifetime
     * @return the generated join code, projected into the API representation
     */
    public InviteDto generateJoinCode(String campaignId, String issuerUserId, InviteCreationRequest request) {
        requireCampaign(campaignId);
        String issuer = requireIssuer(issuerUserId, campaignId, MembershipRole.GAME_MASTER);
        MembershipRole role = parseRole(request.getRole());

        Instant now = Instant.now();
        // A local join code never expires unless an explicit positive lifetime
        // is supplied; otherwise it remains valid via the default TTL.
        Instant expiresAt = resolveExpiry(
                request.getExpiresAfterSeconds() != null ? request.getExpiresAfterSeconds()
                        : DEFAULT_JOIN_CODE_TTL_SECONDS, now);

        CampaignInvite invite = new CampaignInvite();
        invite.setId(UUID.randomUUID().toString());
        invite.setInviteCode(uniqueCode());
        invite.setCampaignId(campaignId);
        invite.setIssuedBy(issuer);
        invite.setRole(role);
        invite.setRedeemMode(InviteRedeemMode.PENDING);
        invite.setStatus(InviteStatus.PENDING);
        invite.setExpiresAt(expiresAt);
        invite.setCreatedAt(now);
        invite.setUpdatedAt(now);

        return toDto(inviteRepository.save(invite));
    }

    /**
     * Redeems an invite or local join code by its code for the given user.
     *
     * <p>When the invite's {@link InviteRedeemMode} is
     * {@link InviteRedeemMode#DIRECT} (a targeted invite), redemption creates
     * or updates an {@link MembershipStatus#ACTIVE} membership with the role
     * granted at issue time. When the mode is {@link InviteRedeemMode#PENDING}
     * (a local join code), redemption creates a {@link
     * MembershipStatus#PENDING} membership awaiting approval.</p>
     *
     * <p>The invite is marked {@link InviteStatus#USED} once redeemed and can
     * no longer be used.</p>
     *
     * @param inviteCode the secret code of the invite or join code
     * @param userId     stable identifier of the user redeeming the code
     * @return the created or updated membership, projected into the API
     * representation
     */
    public MembershipDto redeem(String inviteCode, String userId) {
        String resolvedUserId = requireNonBlank(userId, "userId");
        CampaignInvite invite = findActiveInvite(inviteCode);

        // Guard against a race where the same single-use code is redeemed twice.
        synchronized (redeemLockFor(invite.getId())) {
            invite = inviteRepository.findByInviteCode(invite.getInviteCode()).orElseThrow();
            if (invite.getStatus() != InviteStatus.PENDING) {
                throw new IllegalArgumentException("Invite has already been used or revoked");
            }

            MembershipDto membership;
            if (invite.getRedeemMode() == InviteRedeemMode.PENDING) {
                membership = membershipService.addPendingMember(
                        invite.getCampaignId(), resolvedUserId, invite.getRole());
            } else {
                membership = addActiveMember(invite.getCampaignId(), resolvedUserId, invite.getRole());
            }

            invite.setStatus(InviteStatus.USED);
            invite.setUpdatedAt(Instant.now());
            inviteRepository.save(invite);
            return membership;
        }
    }

    /**
     * Returns the invite identified by its code.
     *
     * @param inviteCode the invite code
     * @return the invite, projected into the API representation
     */
    public InviteDto getInvite(String inviteCode) {
        return toDto(findActiveInvite(inviteCode));
    }

    /**
     * Lists all invites issued for a campaign.
     *
     * @param campaignId stable campaign identifier
     * @return the campaign's invites
     */
    public List<InviteDto> listInvites(String campaignId) {
        requireCampaign(campaignId);
        return inviteRepository.findByCampaignId(campaignId).stream()
                .map(this::toDto)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Revokes a still-pending invite issued for a campaign. The revoker must
     * hold at least the {@link MembershipRole#GAME_MASTER} role in the
     * campaign. A revoked invite can no longer be redeemed.
     *
     * @param campaignId     stable campaign identifier
     * @param inviteCode     the invite to revoke
     * @param revokerUserId  stable identifier of the revoking owner or game master
     * @return the revoked invite, projected into the API representation
     */
    public InviteDto revokeInvite(String campaignId, String inviteCode, String revokerUserId) {
        requireCampaign(campaignId);
        requireIssuer(revokerUserId, campaignId, MembershipRole.GAME_MASTER);
        CampaignInvite invite = findPendingInvite(inviteCode);

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new IllegalArgumentException("Only a pending invite can be revoked");
        }
        invite.setStatus(InviteStatus.REVOKED);
        invite.setUpdatedAt(Instant.now());
        return toDto(inviteRepository.save(invite));
    }

    private static Object redeemLockFor(String inviteId) {
        return inviteId.intern();
    }

    /**
     * Locates a live invite by code, treating an expired invite as unusable.
     */
    private CampaignInvite findActiveInvite(String inviteCode) {
        String code = requireNonBlank(inviteCode, "inviteCode");
        CampaignInvite invite = inviteRepository.findByInviteCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown invite code: " + inviteCode));
        if (invite.getStatus() == InviteStatus.EXPIRED || invite.isExpired()) {
            invite.setStatus(InviteStatus.EXPIRED);
            invite.setUpdatedAt(Instant.now());
            inviteRepository.save(invite);
            throw new IllegalArgumentException("Invite has expired");
        }
        return invite;
    }

    private CampaignInvite findPendingInvite(String inviteCode) {
        CampaignInvite invite = findActiveInvite(inviteCode);
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new IllegalArgumentException("Invite is not pending");
        }
        return invite;
    }

    private MembershipDto addActiveMember(String campaignId, String userId, MembershipRole role) {
        return membershipService.addMember(campaignId, new com.gamemasterx.server.campaign.membership.model.MembershipCreateRequest(userId, role.name()));
    }

    private void requireCampaign(String campaignId) {
        if (campaignRepository.findById(campaignId).isEmpty()) {
            throw new IllegalArgumentException("Campaign not found: " + campaignId);
        }
    }

    /**
     * Verifies the issuer exists and holds at least the required role in the
     * campaign, then returns the issuer identifier.
     */
    private String requireIssuer(String issuerUserId, String campaignId, MembershipRole requiredRole) {
        String issuer = requireNonBlank(issuerUserId, "issuerUserId");
        if (!membershipService.authorizeAs(campaignId, issuer, requiredRole)) {
            throw new IllegalArgumentException(
                    "You must hold at least the " + requiredRole + " role in this campaign to issue invites");
        }
        return issuer;
    }

    private static MembershipRole parseRole(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Role must not be empty");
        }
        return MembershipRole.parse(raw);
    }

    private static Instant resolveExpiry(Long expiresAfterSeconds, Instant now) {
        if (expiresAfterSeconds == null || expiresAfterSeconds <= 0) {
            return null;
        }
        return now.plusSeconds(expiresAfterSeconds);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }

    /**
     * Generates a fresh, unique invite/join code. Join codes use a short,
     * human-friendly uppercase alphanumeric form while invites use a longer
     * lower-case form; both are guaranteed unique by re-checking the
     * repository.
     */
    private String uniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = attempt == 0 ? longCode() : shortCode();
            if (inviteRepository.findByInviteCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate a unique invite code");
    }

    private static String longCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static final String SHORT_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static String shortCode() {
        StringBuilder sb = new StringBuilder(10);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) {
            sb.append(SHORT_CODE_ALPHABET.charAt(rnd.nextInt(SHORT_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private InviteDto toDto(CampaignInvite invite) {
        return InviteDto.from(invite);
    }
}
