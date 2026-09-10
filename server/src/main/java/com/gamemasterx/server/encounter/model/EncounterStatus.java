package com.gamemasterx.server.encounter.model;

/**
 * Lifecycle states an {@link Encounter} can be in.
 *
 * <p>The states form the combat/session lifecycle enforced by
 * {@link Encounter#transitionTo(EncounterStatus)}:
 *
 * <ul>
 *   <li>{@link #DRAFT} &ndash; the encounter has been created but not yet
 *       started. The game master organises participants, initiative and
 *       positions before beginning.</li>
 *   <li>{@link #ACTIVE} &ndash; the encounter is in progress. Rounds and turns
 *       are advancing and participants are acting.</li>
 *   <li>{@link #PAUSED} &ndash; an active encounter that has been temporarily
 *       suspended. It can be resumed or completed.</li>
 *   <li>{@link #COMPLETED} &ndash; the terminal state. The encounter is over and
 *       no further transitions are permitted.</li>
 * </ul>
 */
public enum EncounterStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    COMPLETED
}
