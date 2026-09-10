package com.gamemasterx.server.encounter.idempotency;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * A single idempotency-key record persisted in the {@code idempotency_keys}
 * collection.
 *
 * <p>An idempotency key lets a caller make a request that may be retried (a
 * dropped network reply, a double click, an at-least-once delivery) without the
 * retry being able to re-apply its effect. The first request to supply a key
 * wins and records the outcome it produced; any later request that reuses the
 * same key is answered from this stored record instead of running again. This
 * is what prevents a retry from applying damage twice, consuming a resource
 * twice, rolling the dice twice, or recording a turn twice.</p>
 *
 * <p>Only a terminal {@link #status()} ({@code COMPLETED}) is ever read back: an
 * in-flight or never-committed reservation leaves no durable record, so a retry
 * after a failure is still allowed to proceed.</p>
 */
@Document(collection = "idempotency_keys")
@CompoundIndex(def = "key", unique = true, name = "idx_idempotency_key")
public record IdempotencyRecord(

        /** Stable identifier of the record. */
        @Id
        String id,

        /**
         * The caller-supplied idempotency key. Enforced unique (see the
         * {@code @Document} index) so that exactly one record can exist per key.
         */
        String key,

        /** The enclosing encounter, when the key guards an encounter operation. */
        String encounterId,

        /** A short label describing the guarded operation, for diagnostics. */
        String operation,

        /** The actor who performed the guarded operation. */
        String actor,

        /** The JSON-serialized outcome of the guarded operation, when completed. */
        String resultJson,

        /** The terminal status, currently always {@code COMPLETED}. */
        String status,

        /** When the key was first seen. */
        Instant createdAt,

        /** When the guarded operation completed successfully. */
        Instant completedAt) {

    /**
     * Builds a completed record for the given key and outcome.
     *
     * @param key        the caller-supplied idempotency key
     * @param encounterId the enclosing encounter, or {@code null}
     * @param operation  a short operation label
     * @param actor      the actor who performed the operation
     * @param resultJson the serialized outcome
     * @return the completed idempotency record
     */
    public static IdempotencyRecord completed(String key, String encounterId, String operation,
                                              String actor, String resultJson) {
        Instant now = Instant.now();
        return new IdempotencyRecord(
                UUID.randomUUID().toString(), key, encounterId, operation, actor, resultJson,
                "COMPLETED", now, now);
    }
}
