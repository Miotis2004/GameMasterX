package com.gamemasterx.server.inventory.repository;

import com.gamemasterx.server.inventory.model.Inventory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for the {@link Inventory} aggregate.
 *
 * <p>There is at most one inventory per campaign; {@link #findByCampaignId}
 * returns the single aggregate owning every item and consumable resource of
 * that campaign.</p>
 */
@Repository
public interface InventoryRepository extends MongoRepository<Inventory, String> {

    /**
     * @param campaignId the campaign identifier
     * @return the inventory belonging to the campaign, when one exists
     */
    Optional<Inventory> findByCampaignId(String campaignId);
}
