package com.gamemasterx.server.encounter.idempotency;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.gamemasterx.server.encounter.model.EncounterDto;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Owns idempotency-key deduplication so that a retried request can never
 * re-apply its effect.
 *
 * <p>The workflow is intentionally simple and atomic with the committing
 * transaction:</p>
 * <ol>
 *   <li>{@link #peekCompleted(String, Class)} is consulted before a guarded
 *   operation runs. When the key has already completed, the stored outcome is
 *   returned and the caller short-circuits &ndash; no damage is applied, no
 *   resource is consumed, no dice are rolled and no turn is recorded.</li>
 *   <li>After the guarded operation has committed successfully,
 *   {@link #complete(String, String, String, String, Object)} records the
 *   outcome. Because this runs inside the same transaction as the commit, a
 *   failed operation rolls the record back as well and a subsequent retry is
 *   still free to proceed.</li>
 * </ol>
 *
 * <p>The stored outcome is a plain JSON string and is read back into whatever
 * type the caller asks for, so the same store serves both the encounter
 * aggregate mutations (an {@link EncounterDto} outcome) and the encounter-less
 * turn/action submissions (a {@link
 * com.gamemasterx.server.gameplay.service.GameplayRulesService.ResolvedCheck}
 * or {@link
 * com.gamemasterx.server.gameplay.service.GameplayRulesService.ResolvedAttack}
 * outcome). The outcome is only ever deserialized from the backend-owned store
 * record, never from a caller-supplied value.</p>
 */
@Service
public class IdempotencyService {

    private static final String COMPLETED_STATUS = "COMPLETED";

    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    /**
     * Creates the idempotency service.
     *
     * @param idempotencyRepository the idempotency-key store
     * @param objectMapper          the mapper used to serialize/deserialize outcomes
     */
    public IdempotencyService(IdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the stored outcome for a completed idempotency key, decoded into
     * the type the caller expects, or {@code null} when the key has never
     * completed a successful operation or the stored record is corrupted.
     *
     * <p>The outcome is deserialized from the backend-owned store record, so the
     * returned value is never a caller-supplied value.</p>
     *
     * @param key the caller-supplied idempotency key
     * @param type the type to decode the stored outcome into
     * @param <T> the stored outcome type
     * @return the previously recorded outcome, or {@code null}
     */
    public <T> T peekCompleted(String key, Class<T> type) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Optional<IdempotencyRecord> record = idempotencyRepository.findByKey(key);
        if (record.isPresent() && COMPLETED_STATUS.equals(record.get().status())
                && record.get().resultJson() != null) {
            try {
                return objectMapper.readValue(record.get().resultJson(), type);
            } catch (JacksonException malformed) {
                // A corrupted record must not mask a real operation; fall through
                // and allow the caller to proceed. The duplicate key on completion
                // will still protect against double application.
                return null;
            }
        }
        return null;
    }

    /**
     * Returns the stored outcome for a completed idempotency key as an
     * {@link EncounterDto}, or {@code null} when the key has never completed a
     * successful operation.
     *
     * @param key the caller-supplied idempotency key
     * @return the previously recorded outcome, or {@code null}
     */
    public EncounterDto peekCompleted(String key) {
        return peekCompleted(key, EncounterDto.class);
    }

    /**
     * Returns {@code true} when the supplied idempotency key has already
     * completed a successful operation. This lets the orchestrating
     * {@link com.gamemasterx.server.ai.operation.validate.OperationValidator}
     * report a repeated key without re-applying its effect: a repeat is not an
     * error, but it must never run the guarded operation a second time.
     *
     * @param key the caller-supplied idempotency key
     * @return {@code true} when the key has already completed a successful
     *         operation
     */
    public boolean isCompleted(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        Optional<IdempotencyRecord> record = idempotencyRepository.findByKey(key);
        return record.isPresent() && COMPLETED_STATUS.equals(record.get().status());
    }

    /**
     * Records the completed outcome of a guarded operation so that any later
     * retry of the same key is answered from this record.
     *
     * @param key         the caller-supplied idempotency key
     * @param encounterId the enclosing encounter, or {@code null}
     * @param operation   a short operation label
     * @param actor       the actor who performed the operation
     * @param outcome     the outcome to record; serialized to JSON by the backend
     *                    from the value produced by the guarded operation
     * @throws IllegalArgumentException when {@code key} is blank
     */
    public void complete(String key, String encounterId, String operation, String actor, Object outcome) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("An idempotency key must not be blank when recording completion");
        }
        String resultJson;
        try {
            resultJson = objectMapper.writeValueAsString(outcome);
        } catch (JacksonException serializable) {
            throw new IllegalStateException(
                    "Unable to serialize the idempotency outcome for key '" + key + "'", serializable);
        }
        try {
            idempotencyRepository.save(IdempotencyRecord.completed(
                    key, encounterId, operation, actor, resultJson));
        } catch (DuplicateKeyException alreadyCompleted) {
            // A concurrent retry of the same key completed first. The outcome it
            // recorded is exactly what the caller would have recorded, so this is
            // a harmless no-op rather than an error.
        }
    }
}
