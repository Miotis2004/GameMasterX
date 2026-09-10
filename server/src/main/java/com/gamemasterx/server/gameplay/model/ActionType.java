package com.gamemasterx.server.gameplay.model;

/**
 * The kind of gameplay action being recorded.
 *
 * <p>Kept as a small closed enum so action records carry a typed, unambiguous
 * action category. Unknown or future action shapes fall back to {@link
 * #OTHER}.</p>
 */
public enum ActionType {
    /** An attack roll against a target. */
    ATTACK,
    /** A skill or ability check. */
    SKILL_CHECK,
    /** Initiative rolled for the combat order at the start of an encounter. */
    INITIATIVE,
    /** A saving throw. */
    SAVING_THROW,
    /** Movement of a participant across the grid. */
    MOVEMENT,
    /** Damage applied to a target. */
    DAMAGE,
    /** A short rest: spend Hit Dice to recover hit points under the subset. */
    REST_SHORT,
    /** A long rest: recover hit points and resources under the subset. */
    REST_LONG,
    /** A general ability check. */
    ABILITY_CHECK,
    /** Narrative context appended around an action, carrying no participant state change. */
    NARRATIVE,
    /** Any action not covered by the categories above. */
    OTHER
}
