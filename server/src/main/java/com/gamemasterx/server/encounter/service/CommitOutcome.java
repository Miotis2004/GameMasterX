package com.gamemasterx.server.encounter.service;

import com.gamemasterx.server.encounter.model.EncounterDto;

/**
 * The result of a transactional encounter commit.
 *
 * <p>It carries the outcome DTO returned to the caller, whether the commit was
 * answered from a prior idempotent attempt, whether it was made with a
 * multi-document transaction, and the resulting revision &ndash; information that
 * the API surfaces so callers can see the optimistic-concurrency generation they
 * are now looking at.</p>
 */
public record CommitOutcome(

        /** The encounter DTO returned to the caller. */
        EncounterDto dto,

        /** {@code true} when the result was served from a prior idempotent attempt. */
        boolean idempotent,

        /** {@code true} when the commit was made inside a multi-document transaction. */
        boolean transactional,

        /** The transaction mode in use: {@code "transactional"} or {@code "non-transactional"}. */
        String transactionMode,

        /** The encounter revision immediately after the commit. */
        int revisionAfter,

        /** The idempotency key that was applied, or {@code null} when none was supplied. */
        String idempotencyKey,

        /** A short human-readable note describing how the commit was made. */
        String note) {

    /**
     * @param dto          the returned DTO
     * @param idempotent   whether this was an idempotent repeat
     * @param transactional whether the commit used a transaction
     * @param transactionMode the transaction mode
     * @param revisionAfter the resulting revision
     * @param idempotencyKey the applied key, or {@code null}
     * @param note         a descriptive note
     * @return the commit outcome
     */
    public static CommitOutcome of(EncounterDto dto, boolean idempotent, boolean transactional,
                                   String transactionMode, int revisionAfter, String idempotencyKey,
                                   String note) {
        return new CommitOutcome(dto, idempotent, transactional, transactionMode, revisionAfter,
                idempotencyKey, note);
    }
}
