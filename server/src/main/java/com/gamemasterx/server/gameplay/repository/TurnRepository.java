package com.gamemasterx.server.gameplay.repository;

import com.gamemasterx.server.gameplay.model.Turn;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data MongoDB repository for immutable {@link Turn} documents.
 *
 * <p>Turns are append-only: the only write operation offered by this repository
 * is {@link #save} of a brand-new turn document. Turn documents are never
 * updated in place or deleted, so the historical turn sequence for an encounter
 * is preserved exactly as recorded.</p>
 */
@Repository
public interface TurnRepository extends MongoRepository<Turn, String> {

    /**
     * @param encounterId the encounter identifier
     * @return the turns for the encounter, ordered by round and turn position
     */
    List<Turn> findByEncounterIdOrderByRoundAscTurnIndexAsc(String encounterId);

    /**
     * @param campaignId the campaign identifier
     * @return the turns for the campaign, ordered by round and turn position
     */
    List<Turn> findByCampaignIdOrderByRoundAscTurnIndexAsc(String campaignId);

    /**
     * @param encounterId the encounter identifier
     * @return the number of turns recorded for the encounter
     */
    long countByEncounterId(String encounterId);
}
