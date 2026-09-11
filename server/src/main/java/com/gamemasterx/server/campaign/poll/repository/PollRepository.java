package com.gamemasterx.server.campaign.poll.repository;

import com.gamemasterx.server.campaign.poll.model.Poll;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data repository for Poll documents.
 */
public interface PollRepository extends MongoRepository<Poll, String> {

    List<Poll> findByCampaignId(String campaignId);

    Poll findByIdAndCampaignId(String id, String campaignId);
}
