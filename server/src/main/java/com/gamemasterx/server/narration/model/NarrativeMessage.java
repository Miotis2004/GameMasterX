package com.gamemasterx.server.narration.model;

import java.time.Instant;

/**
 * A single narrative message recorded in a durable narrative log.
 *
 * <p>Messages are immutable once persisted. The {@link #type} field determines
 * the semantic category of the message. GM notes are persisted but visibility
 * is enforced on read by the service layer.</p>
 */
public record NarrativeMessage(
        String id,
        NarrativeMessageType type,
        String content,
        String authorId,
        Instant timestamp,
        boolean isPrivate) {

    public NarrativeMessage {
        if (type == null) {
            throw new IllegalArgumentException("Message type must not be null");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content must not be blank");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp must not be null");
        }
    }
}
