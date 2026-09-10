package com.gamemasterx.server.exception;

/**
 * Thrown when a commit's expected revision does not match the current revision
 * of the aggregate, i.e. another writer has already committed a change and the
 * caller's optimistic guess is stale.
 *
 * <p>Raising this exception <em>before</em> anything is persisted guarantees
 * there is no partial commit: the conflicting write is rejected wholesale and a
 * clear diagnostic (expected vs. current revision, and the subject id) is
 * returned to the caller.</p>
 */
public class OptimisticConcurrencyException extends RuntimeException {

    private final int expectedRevision;
    private final int currentRevision;
    private final String subjectId;

    /**
     * Creates the exception.
     *
     * @param subjectId        the identifier of the aggregate being committed
     * @param expectedRevision the revision the caller expected
     * @param currentRevision  the revision currently stored
     */
    public OptimisticConcurrencyException(String subjectId, int expectedRevision, int currentRevision) {
        super("Conflicting update for " + subjectId + ": expected revision " + expectedRevision
                + " but the current revision is " + currentRevision + ". A concurrent update has already "
                + "been committed. Re-read the latest state and retry with the current revision.");
        this.expectedRevision = expectedRevision;
        this.currentRevision = currentRevision;
        this.subjectId = subjectId;
    }

    /**
     * @return the revision the caller expected
     */
    public int getExpectedRevision() {
        return expectedRevision;
    }

    /**
     * @return the revision currently stored
     */
    public int getCurrentRevision() {
        return currentRevision;
    }

    /**
     * @return the identifier of the aggregate whose update was rejected
     */
    public String getSubjectId() {
        return subjectId;
    }
}
