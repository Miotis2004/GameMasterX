package com.gamemasterx.server.narration.repository;

import com.gamemasterx.server.narration.model.NarrativeRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for durable narrative records.
 */
@Repository
public interface NarrativeRecordRepository extends MongoRepository<NarrativeRecord, String> {

    List<NarrativeRecord> findByCampaignIdOrderByCreatedAtAsc(String campaignId);

    List<NarrativeRecord> findByEncounterIdOrderByCreatedAtAsc(String encounterId);

    Optional<NarrativeRecord> findById(String id);
}
