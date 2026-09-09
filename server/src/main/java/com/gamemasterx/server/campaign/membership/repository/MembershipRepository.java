package com.gamemasterx.server.campaign.membership.repository;

import com.gamemasterx.server.campaign.membership.model.Membership;
import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.campaign.membership.model.MembershipStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRepository extends MongoRepository<Membership, String> {

    /**
     * Finds all memberships for a campaign, regardless of role.
     *
     * @param campaignId the stable campaign identifier
     * @return the memberships of the campaign
     */
    List<Membership> findByCampaignId(String campaignId);

    /**
     * Finds a single membership for a user within a campaign.
     *
     * @param campaignId the stable campaign identifier
     * @param userId     the stable user identifier
     * @return the matching membership, if present
     */
    Optional<Membership> findByCampaignIdAndUserId(String campaignId, String userId);

    /**
     * Finds a single, fully-admitted (ACTIVE) membership for a user within a
     * campaign. PENDING and REVOKED memberships are not returned, so a caller can
     * use the presence of a result to confirm the user genuinely belongs to the
     * campaign.
     *
     * @param campaignId the stable campaign identifier
     * @param userId     the stable user identifier
     * @return the active membership, if present
     */
    Optional<Membership> findByCampaignIdAndUserIdAndStatus(String campaignId, String userId,
                                                            MembershipStatus status);

    /**
     * Finds all campaigns in which a user holds a given role.
     *
     * @param userId the stable user identifier
     * @param role   the role to match
     * @return the memberships matching the user and role
     */
    List<Membership> findByUserIdAndRole(String userId, MembershipRole role);

    /**
     * Finds all memberships held by a user across every campaign.
     *
     * @param userId the stable user identifier
     * @return the user's memberships
     */
    List<Membership> findByUserId(String userId);
}
