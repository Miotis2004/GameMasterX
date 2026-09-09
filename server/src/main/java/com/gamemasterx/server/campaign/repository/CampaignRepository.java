package com.gamemasterx.server.campaign.repository;

import com.gamemasterx.server.campaign.model.Campaign;
import com.gamemasterx.server.campaign.model.CampaignStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignRepository extends MongoRepository<Campaign, String> {

    List<Campaign> findByStatus(CampaignStatus status);
}
