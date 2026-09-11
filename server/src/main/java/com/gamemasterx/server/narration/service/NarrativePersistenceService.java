package com.gamemasterx.server.narration.service;

import com.gamemasterx.server.narration.model.NarrativeMessage;
import com.gamemasterx.server.narration.model.NarrativeMessageType;
import com.gamemasterx.server.narration.model.NarrativeRecord;
import com.gamemasterx.server.narration.repository.NarrativeRecordRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for persisting completed narratives and enforcing visibility on read.
 *
 * <p>Completed narratives are persisted durably with schema version, revision,
 * and timestamps. Transient streaming tokens are not persisted as final.</p>
 *
 * <p>GM notes are persisted but visibility is enforced on read: only callers
 * with GM role may see GM_NOTE messages.</p>
 */
@Service
public class NarrativePersistenceService {

    private static final int SCHEMA_VERSION = 1;

    private final NarrativeRecordRepository repository;

    public NarrativePersistenceService(NarrativeRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * Persists a completed narrative record.
     *
     * @param campaignId the owning campaign
     * @param encounterId the owning encounter, may be null
     * @param turnId the owning turn, may be null
     * @param messages the completed messages
     * @return the persisted record
     */
    public NarrativeRecord persistCompleted(String campaignId, String encounterId, String turnId, List<NarrativeMessage> messages) {
        if (campaignId == null || campaignId.isBlank()) {
            throw new IllegalArgumentException("campaignId must not be blank");
        }
        Instant now = Instant.now();
        NarrativeRecord record = new NarrativeRecord(
                UUID.randomUUID().toString(),
                SCHEMA_VERSION,
                1,
                now,
                now,
                campaignId,
                encounterId,
                turnId,
                messages
        );
        return repository.save(record);
    }

    /**
     * Retrieves a narrative record with visibility enforcement.
     *
     * @param id the record id
     * @param isGm whether the caller has GM privileges
     * @return the record with GM notes filtered if caller is not GM
     */
    public NarrativeRecord readWithVisibility(String id, boolean isGm) {
        NarrativeRecord record = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Narrative record not found: " + id));
        if (isGm) {
            return record;
        }
        List<NarrativeMessage> filtered = new ArrayList<>();
        for (NarrativeMessage msg : record.messages()) {
            if (msg.type() == NarrativeMessageType.GM_NOTE) {
                continue;
            }
            if (msg.type() == NarrativeMessageType.PRIVATE_WHISPER) {
                continue;
            }
            if (msg.isPrivate()) {
                continue;
            }
            filtered.add(msg);
        }
        return new NarrativeRecord(
                record.id(),
                record.schemaVersion(),
                record.revision(),
                record.createdAt(),
                record.updatedAt(),
                record.campaignId(),
                record.encounterId(),
                record.turnId(),
                filtered
        );
    }

    /**
     * Updates an existing record with new messages, incrementing revision.
     */
    public NarrativeRecord appendMessages(String id, List<NarrativeMessage> newMessages) {
        NarrativeRecord existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Narrative record not found: " + id));
        List<NarrativeMessage> combined = new ArrayList<>(existing.messages());
        combined.addAll(newMessages);
        NarrativeRecord updated = new NarrativeRecord(
                existing.id(),
                existing.schemaVersion(),
                existing.revision() + 1,
                existing.createdAt(),
                Instant.now(),
                existing.campaignId(),
                existing.encounterId(),
                existing.turnId(),
                combined
        );
        return repository.save(updated);
    }
}
