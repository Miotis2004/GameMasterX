package com.gamemasterx.server.narration.model;

/**
 * The disclosure partition a narration context line belongs to.
 *
 * <p>This is the single seam on which the strict player-vs-GM separation rests.
 * Every line the assembler produces is tagged with exactly one kind, and the
 * assembled context is split on this tag: {@link #PLAYER_VISIBLE} lines may be
 * sent to the AI, {@link #GM_SECRET} lines may not.</p>
 */
public enum NarrationLineKind {

    /**
     * Information the players are presumed to know or perceive: campaign
     * identity, the active scene, visible participant state, the acting
     * character's own sheet, known world facts and the events of recent
     * narrative. These lines are the only kind ever sent to the AI.
     */
    PLAYER_VISIBLE,

    /**
     * GM-only information: unrevealed secret notes, NPC intentions, true
     * creature challenge ratings, hidden initiative/positions, secret objectives
     * and undiscovered world facts. These lines are assembled and separated but
     * are <b>never</b> placed in the context sent to the AI.
     */
    GM_SECRET
}
