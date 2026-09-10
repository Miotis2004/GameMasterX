package com.gamemasterx.server.encounter;

import com.gamemasterx.server.encounter.model.EncounterStatus;

/**
 * Thrown when a state attempt is made that the encounter lifecycle does not
 * permit.
 *
 * <p>This is deliberately distinct from {@link IllegalArgumentException} so that
 * the global exception handler can map an illegal encounter transition to its
 * own {@code 400 Bad Request} response with a message that clearly describes the
 * rejected transition, rather than a generic invalid-input reply.</p>
 */
public class EncounterStateTransitionException extends RuntimeException {

    private final EncounterStatus from;
    private final EncounterStatus to;

    /**
     * @param from the current status of the encounter
     * @param to   the requested target status
     */
    public EncounterStateTransitionException(EncounterStatus from, EncounterStatus to) {
        super("Cannot transition encounter from " + from + " to " + to
                + "; the transition is not permitted by the encounter lifecycle");
        this.from = from;
        this.to = to;
    }

    /**
     * @return the status the encounter was currently in
     */
    public EncounterStatus getFrom() {
        return from;
    }

    /**
     * @return the status that was requested
     */
    public EncounterStatus getTo() {
        return to;
    }
}
