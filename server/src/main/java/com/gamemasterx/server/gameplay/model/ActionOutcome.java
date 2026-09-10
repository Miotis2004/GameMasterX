package com.gamemasterx.server.gameplay.model;

/**
 * The final resolved outcome of an {@link Action}.
 *
 * <p>Used to record, without ambiguity, whether an action succeeded, failed,
 * only partially achieved its intent, or scored a critical hit. Persisted
 * verbatim in the audit log so the historical outcome is never reinterpreted.</p>
 */
public enum ActionOutcome {
    /** The action fully achieved its intent. */
    SUCCESS,
    /** The action failed to achieve its intent. */
    FAILURE,
    /** The action achieved its intent only partially (for example a partial effect). */
    PARTIAL,
    /**
     * A critical outcome: the action both achieved its intent and exceeded the
     * critical threshold (for example a natural-20 attack roll that hits). A
     * critical hit is always a hit; the additional critical effect (such as
     * doubled damage) is resolved separately by the damage step.
     */
    CRIT,
    /** The outcome was not determined, for example the action was cancelled. */
    UNKNOWN
}
