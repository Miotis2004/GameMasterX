package com.gamemasterx.server.encounter.idempotency;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link IdempotencyRecord} documents in the
 * {@code idempotency_keys} collection.
 */
@Repository
public interface IdempotencyRepository extends MongoRepository<IdempotencyRecord, String> {

    /**
     * Finds the record for a caller-supplied idempotency key.
     *
     * @param key the idempotency key
     * @return the matching record, if any
     */
    Optional<IdempotencyRecord> findByKey(String key);
}
