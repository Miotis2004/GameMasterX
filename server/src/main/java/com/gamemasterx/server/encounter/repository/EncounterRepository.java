package com.gamemasterx.server.encounter.repository;

import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.encounter.model.EncounterStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EncounterRepository extends MongoRepository<Encounter, String> {

    /**
     * Finds all encounters belonging to a campaign, regardless of lifecycle
     * state.
     *
     * @param campaignId the stable campaign identifier
     * @return the encounters of the campaign
     */
    List<Encounter> findByCampaignId(String campaignId);

    /**
     * Finds encounters for a campaign in a given lifecycle state.
     *
     * @param campaignId the stable campaign identifier
     * @param status     the lifecycle state to match
     * @return the matching encounters
     */
    List<Encounter> findByCampaignIdAndStatus(String campaignId, EncounterStatus status);

    /**
     * Finds a single encounter by its stable identifier.
     *
     * @param encounterId the stable encounter identifier
     * @return the matching encounter, if present
     */
    Optional<Encounter> findById(String encounterId);
}
