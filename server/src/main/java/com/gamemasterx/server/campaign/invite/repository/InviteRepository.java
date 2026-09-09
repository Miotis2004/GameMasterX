package com.gamemasterx.server.campaign.invite.repository;

import com.gamemasterx.server.campaign.invite.model.CampaignInvite;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InviteRepository extends MongoRepository<CampaignInvite, String> {

    /**
     * Finds an invite by its single-use secret code.
     *
     * @param inviteCode the invite code
     * @return the matching invite, if present
     */
    Optional<CampaignInvite> findByInviteCode(String inviteCode);

    /**
     * Lists all invites issued for a campaign.
     *
     * @param campaignId the stable campaign identifier
     * @return the campaign's invites
     */
    List<CampaignInvite> findByCampaignId(String campaignId);
}
