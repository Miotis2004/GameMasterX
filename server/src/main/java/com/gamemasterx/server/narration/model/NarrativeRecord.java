package com.gamemasterx.server.narration.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Durable record of a completed narrative session.
 *
 * <p>The record carries schema version, revision, and timestamps as required
 * for durable persistence. Transient streaming tokens are not persisted as
 * final; only the completed narrative is stored.</p>
 *
 * <p>GM notes are persisted but visibility is enforced on read by the service
 * layer.</p>
 */
@Document(collection = "narrative_records")
public record NarrativeRecord(
        @Id
        String id,
        int schemaVersion,
        int revision,
        Instant createdAt,
        Instant updatedAt,
        String campaignId,
        String encounterId,
        String turnId,
        List<NarrativeMessage> messages) {

    public NarrativeRecord {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt must not be null");
        }
        messages = List.copyOf(messages);
    }

    public NarrativeRecord withUpdatedAt(Instant updatedAt) {
        return new NarrativeRecord(id, schemaVersion, revision, createdAt, updatedAt, campaignId, encounterId, turnId, messages);
    }

    public NarrativeRecord withRevision(int revision) {
        return new NarrativeRecord(id, schemaVersion, revision, createdAt, updatedAt, campaignId, encounterId, turnId, messages);
    }
}
