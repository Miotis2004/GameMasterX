package com.gamemasterx.server.gameplay.model;

/**
 * The decision made on a proposed {@link Mutation} during audit review.
 *
 * <p>The audit log retains both accepted and rejected decisions so the full
 * history of what was proposed and resolved is preserved. Rejected decisions
 * additionally carry the reason they were refused.</p>
 */
public enum MutationDecision {
    /** The mutation was accepted and its change applied. */
    ACCEPTED,
    /** The mutation was rejected and no change was applied. */
    REJECTED
}
