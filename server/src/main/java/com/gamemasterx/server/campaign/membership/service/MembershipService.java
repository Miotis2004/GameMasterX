package com.gamemasterx.server.campaign.membership.service;

import com.gamemasterx.server.campaign.membership.model.Membership;
import com.gamemasterx.server.campaign.membership.model.MembershipCreateRequest;
import com.gamemasterx.server.campaign.membership.model.MembershipDto;
import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.campaign.membership.model.MembershipStatus;
import com.gamemasterx.server.campaign.membership.repository.MembershipRepository;
import com.gamemasterx.server.exception.AuthorizationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service for the Campaign membership aggregate.
 *
 * <p>This service owns all mutation of {@link Membership} documents and is the
 * single place where role-based authorization decisions are computed using the
 * {@link MembershipRole} hierarchy. Callers request an action at a given
 * authority level (for example {@code authorizeAs(GAME_MASTER)}) and the service
 * consults the user's role to decide whether the action is permitted.</p>
 */
@Service
public class MembershipService {

    /** Current schema version for the Membership document shape. */
    private static final int SCHEMA_VERSION = 1;

    private final MembershipRepository membershipRepository;

    public MembershipService(MembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    /**
     * Adds a user to a campaign with the supplied role, or overwrites an
     * existing membership's role for the same campaign and user.
     *
     * @param campaignId stable campaign identifier
     * @param request    the user to add and desired role
     * @return the persisted membership, projected into the API representation
     */
    /**
     * Creates a pending membership for a user who redeemed a join code. The
     * user awaits approval before becoming active. If the user already holds a
     * membership in the campaign, that membership is returned unchanged.
     *
     * @param campaignId stable campaign identifier
     * @param userId     stable user identifier of the joiner
     * @param role       the role the joiner receives (typically pending approval)
     * @return the pending membership, projected into the API representation
     */
    public MembershipDto addPendingMember(String campaignId, String userId, MembershipRole role) {
        String resolvedUserId = requireNonBlank(userId, "userId");
        Membership existing = membershipRepository
                .findByCampaignIdAndUserId(campaignId, resolvedUserId)
                .orElse(null);
        if (existing != null) {
            return toDto(existing);
        }

        Membership membership = new Membership();
        membership.setId(UUID.randomUUID().toString());
        membership.setSchemaVersion(SCHEMA_VERSION);
        membership.setRevision(1);
        Instant now = Instant.now();
        membership.setCreatedAt(now);
        membership.setUpdatedAt(now);
        membership.setCampaignId(campaignId);
        membership.setUserId(resolvedUserId);
        membership.setRole(role);
        membership.setStatus(MembershipStatus.PENDING);
        return toDto(membershipRepository.save(membership));
    }

    public MembershipDto addMember(String campaignId, MembershipCreateRequest request) {
        String userId = requireNonBlank(request.getUserId(), "userId");
        MembershipRole role = parseRole(request.getRole());

        Instant now = Instant.now();
        Membership existing = membershipRepository
                .findByCampaignIdAndUserId(campaignId, userId)
                .orElse(null);

        Membership membership;
        if (existing != null) {
            existing.setRole(role);
            existing.setRevision(existing.getRevision() + 1);
            existing.setUpdatedAt(now);
            membership = existing;
        } else {
            membership = new Membership();
            membership.setId(UUID.randomUUID().toString());
            membership.setSchemaVersion(SCHEMA_VERSION);
            membership.setRevision(1);
            membership.setCreatedAt(now);
            membership.setUpdatedAt(now);
            membership.setCampaignId(campaignId);
            membership.setUserId(userId);
            membership.setRole(role);
        }

        return toDto(membershipRepository.save(membership));
    }

    /**
     * Sets a user's role within a campaign. The user must already be a member,
     * and the calling {@code actor} must be authorized to change roles in the
     * campaign. Changing another member's role requires the actor to hold at
     * least the {@link MembershipRole#GAME_MASTER} role; a member may only
     * reassign their own role while keeping it at or below their current role
     * (a member cannot elevate or demote themselves beyond their own authority).
     *
     * @param campaignId stable campaign identifier
     * @param actor      stable user identifier of the caller performing the change
     * @param userId     stable user identifier of the member whose role changes
     * @param newRole    the role to assign (accepted as a case-insensitive name)
     * @return the updated membership, projected into the API representation
     */
    public MembershipDto updateRole(String campaignId, String actor, String userId, String newRole) {
        MembershipRole desired = parseRole(newRole);
        Membership membership = requireMembership(campaignId, userId);
        enforceRoleChangeAuthority(campaignId, actor, membership, desired);

        membership.setRole(desired);
        membership.setRevision(membership.getRevision() + 1);
        membership.setUpdatedAt(Instant.now());
        return toDto(membershipRepository.save(membership));
    }

    /**
     * Accepts a pending membership, transitioning it to {@link
     * MembershipStatus#ACTIVE}. A member may only accept their own pending
     * membership; this cannot be performed on behalf of another user.
     *
     * <p>Acceptance is recorded with a bumped revision counter and an updated
     * timestamp so the state change is auditable.</p>
     *
     * @param campaignId stable campaign identifier
     * @param userId     stable user identifier of the member accepting
     * @param actor      stable user identifier of the caller (must equal {@code userId})
     * @return the accepted membership, projected into the API representation
     */
    public MembershipDto acceptMembership(String campaignId, String userId, String actor) {
        String resolvedUserId = requireNonBlank(userId, "userId");
        Membership membership = requireMembership(campaignId, resolvedUserId);

        if (membership.getStatus() != MembershipStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Only a pending membership can be accepted");
        }
        if (!resolvedUserId.equals(actor)) {
            throw new AuthorizationException(
                    MembershipRole.OBSERVER,
                    "A member may only accept their own pending membership");
        }

        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setRevision(membership.getRevision() + 1);
        membership.setUpdatedAt(Instant.now());
        return toDto(membershipRepository.save(membership));
    }

    /**
     * Revokes the caller's own membership in a campaign, transitioning it to
     * {@link MembershipStatus#REVOKED}. A member may only revoke their own
     * membership; this action cannot be performed on behalf of another user.
     *
     * <p>Revocation is recorded with a bumped revision counter and an updated
     * timestamp so the state change is auditable.</p>
     *
     * @param campaignId stable campaign identifier
     * @param userId     stable user identifier of the member revoking
     * @param actor      stable user identifier of the caller (must equal {@code userId})
     * @return the revoked membership, projected into the API representation
     */
    public MembershipDto revokeMembership(String campaignId, String userId, String actor) {
        String resolvedUserId = requireNonBlank(userId, "userId");
        Membership membership = requireMembership(campaignId, resolvedUserId);

        if (resolvedUserId.equals(actor)) {
            if (membership.getStatus() == MembershipStatus.REVOKED) {
                throw new IllegalArgumentException("Membership has already been revoked");
            }
            membership.setStatus(MembershipStatus.REVOKED);
            membership.setRevision(membership.getRevision() + 1);
            membership.setUpdatedAt(Instant.now());
            return toDto(membershipRepository.save(membership));
        }
        throw new AuthorizationException(
                MembershipRole.OBSERVER,
                "A member may only revoke their own membership");
    }

    /**
     * Enforces that the {@code actor} is authorized to change {@code target}
     * to {@code desired} within the campaign.
     *
     * <p>When the actor is changing their own role, the new role must not
     * grant more authority than the actor currently holds (a member cannot
     * elevate themselves). When the actor is changing another member's role,
     * the actor must hold at least the {@link MembershipRole#GAME_MASTER} role.
     */
    private void enforceRoleChangeAuthority(String campaignId, String actor,
                                            Membership target, MembershipRole desired) {
        String resolvedActor = requireNonBlank(actor, "actor");
        if (target.getUserId().equals(resolvedActor)) {
            if (target.getRole() != null
                    && !desired.isAtLeast(target.getRole())) {
                throw new AuthorizationException(
                        MembershipRole.OBSERVER,
                        "You cannot change your own role to one with less authority");
            }
            return;
        }
        assertAuthorized(campaignId, resolvedActor, MembershipRole.GAME_MASTER);
    }

    /**
     * Removes a user's membership from a campaign. The caller must be
     * authorized to manage members, holding at least the
     * {@link MembershipRole#GAME_MASTER} role in the campaign.
     *
     * @param campaignId stable campaign identifier
     * @param actor      stable user identifier of the caller
     * @param userId     stable user identifier
     */
    public void removeMember(String campaignId, String actor, String userId) {
        assertAuthorized(campaignId, actor, MembershipRole.GAME_MASTER);
        Membership membership = requireMembership(campaignId, userId);
        membershipRepository.delete(membership);
    }

    /**
     * Returns the role a user holds in a campaign.
     *
     * @param campaignId stable campaign identifier
     * @param userId     stable user identifier
     * @return the user's role, or {@link MembershipRole#OBSERVER} if the user has
     * no membership (treated as the lowest role)
     */
    public MembershipRole getRoleForUser(String campaignId, String userId) {
        return membershipRepository.findByCampaignIdAndUserId(campaignId, userId)
                .map(Membership::getRole)
                .orElse(MembershipRole.OBSERVER);
    }

    /**
     * Returns the highest-authority role a user holds across all campaigns they
     * belong to. Useful for UIs and gateway checks that need a single
     * representative authority.
     *
     * @param userId stable user identifier
     * @return the highest role found, or {@link MembershipRole#OBSERVER} if the
     * user is a member of no campaign
     */
    public MembershipRole getHighestRole(String userId) {
        List<Membership> memberships = membershipRepository.findByUserId(userId);
        return memberships.stream()
                .map(Membership::getRole)
                .filter(role -> role != null)
                .max((a, b) -> Integer.compare(a.getLevel(), b.getLevel()))
                .orElse(MembershipRole.OBSERVER);
    }

    /**
     * Authorizes an action against a user's role in a specific campaign using
     * the role hierarchy. The user is granted the action when their role is at
     * or above {@code requiredRole}.
     *
     * @param campaignId   stable campaign identifier
     * @param userId       stable user identifier
     * @param requiredRole the minimum role required for the action
     * @return {@code true} if the user is authorized
     */
    public boolean authorizeAs(String campaignId, String userId, MembershipRole requiredRole) {
        Membership membership = membershipRepository.findByCampaignIdAndUserId(campaignId, userId)
                .orElse(null);
        return membership != null && membership.isAuthorizedFor(requiredRole);
    }

    /**
     * Lists the members of a campaign, projected into the API representation.
     *
     * @param campaignId stable campaign identifier
     * @return the campaign's members
     */
    public List<MembershipDto> listMembers(String campaignId) {
        return membershipRepository.findByCampaignId(campaignId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Asserts that {@code actor} holds at least {@code requiredRole} in
     * {@code campaignId}, throwing a consistent {@link AuthorizationException}
     * (mapped to {@code 403 Forbidden}) when the actor is missing or not
     * authorized. This is the single entry point through which every
     * authorization decision is declared, so each operation advertises the role
     * it requires via the {@code requiredRole} argument.
     *
     * @param campaignId stable campaign identifier
     * @param actor      stable user identifier of the caller, or {@code null}
     *                   when the caller is not authenticated
     * @param requiredRole the minimum role required to perform the action
     * @throws AuthorizationException if the actor is unauthenticated or does not
     *                                hold {@code requiredRole}
     */
    public void assertAuthorized(String campaignId, String actor, MembershipRole requiredRole) {
        if (actor == null || actor.isBlank()) {
            throw new AuthorizationException(requiredRole, "Authentication required to perform this action");
        }
        if (!authorizeAs(campaignId, actor, requiredRole)) {
            throw new AuthorizationException(requiredRole);
        }
    }

    /**
     * Ensures that {@code userId} holds the highest-authority
     * {@link MembershipRole#OWNER} membership in {@code campaignId}, creating it
     * when absent or upgrading an existing membership to OWNER. This is used to
     * establish ownership when a campaign is created so that subsequent
     * operations can be authorized against the membership hierarchy.
     *
     * @param campaignId stable campaign identifier
     * @param userId     stable user identifier of the campaign owner
     * @return the owner membership, projected into the API representation
     */
    public MembershipDto grantOwner(String campaignId, String userId) {
        String resolvedUserId = requireNonBlank(userId, "userId");
        Membership existing = membershipRepository
                .findByCampaignIdAndUserId(campaignId, resolvedUserId)
                .orElse(null);
        if (existing != null) {
            existing.setRole(MembershipRole.OWNER);
            existing.setStatus(MembershipStatus.ACTIVE);
            existing.setRevision(existing.getRevision() + 1);
            existing.setUpdatedAt(Instant.now());
            return toDto(membershipRepository.save(existing));
        }

        Membership membership = new Membership();
        membership.setId(UUID.randomUUID().toString());
        membership.setSchemaVersion(SCHEMA_VERSION);
        membership.setRevision(1);
        Instant now = Instant.now();
        membership.setCreatedAt(now);
        membership.setUpdatedAt(now);
        membership.setCampaignId(campaignId);
        membership.setUserId(resolvedUserId);
        membership.setRole(MembershipRole.OWNER);
        membership.setStatus(MembershipStatus.ACTIVE);
        return toDto(membershipRepository.save(membership));
    }

    private Membership requireMembership(String campaignId, String userId) {
        return membershipRepository.findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Membership not found for campaign " + campaignId + " and user " + userId));
    }

    private static MembershipRole parseRole(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Role must not be empty");
        }
        return MembershipRole.parse(raw);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }

    /**
     * Projects a persisted document entity into the API-facing DTO.
     */
    private MembershipDto toDto(Membership membership) {
        return MembershipDto.from(membership);
    }
}
