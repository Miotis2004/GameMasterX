package com.gamemasterx.server.encounter.model;

/**
 * Identifies who controls a given {@link Participant#actorControl} during an
 * encounter.
 *
 * <p>Actor control is part of the aggregate model because deciding which actor
 * may issue an action for a participant (the player for their own character,
 * the game master for NPCs/monsters, or an automated resolver) is a first-class
 * concern of encounter resolution, not merely a display label.</p>
 */
public enum ActorControl {
    /** The participant is controlled by the player who owns the character. */
    SELF,
    /** The participant is controlled by the game master. */
    GM,
    /** The participant is controlled by an automated resolution rule. */
    AUTOMATED
}
