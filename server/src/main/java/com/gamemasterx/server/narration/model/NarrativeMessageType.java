package com.gamemasterx.server.narration.model;

/**
 * Types of narrative messages that can be recorded in a durable narrative log.
 *
 * <p>Message types distinguish between player-visible narrative, player actions,
 * dice results, system events, private whispers, and GM-only notes. GM notes are
 * persisted but visibility is enforced on read.</p>
 */
public enum NarrativeMessageType {
    NARRATIVE,
    PLAYER_ACTION,
    DICE,
    SYSTEM,
    PRIVATE_WHISPER,
    GM_NOTE
}
