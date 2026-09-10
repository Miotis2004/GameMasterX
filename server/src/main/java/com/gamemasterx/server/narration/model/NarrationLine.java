package com.gamemasterx.server.narration.model;

/**
 * A single classified narration context line: human-readable text plus the
 * {@link NarrationLineKind#PLAYER_VISIBLE player-visible} or
 * {@link NarrationLineKind#GM_SECRET GM-only} partition it belongs to.
 *
 * <p>Lines are the atomic unit the assembler produces and the partitioner
 * splits. The text is never null; the kind is always one of the two enum
 * constants.</p>
 *
 * @param text   the human-readable context line (never {@code null})
 * @param kind   the disclosure partition this line belongs to (never {@code null})
 */
public record NarrationLine(String text, NarrationLineKind kind) {

    public NarrationLine {
        if (text == null) {
            throw new IllegalArgumentException("Narration line text must not be null");
        }
    }

    /**
     * @param text the context line
     * @return a player-visible line
     */
    public static NarrationLine playerVisible(String text) {
        return new NarrationLine(text, NarrationLineKind.PLAYER_VISIBLE);
    }

    /**
     * @param text the context line
     * @return a GM-only secret line
     */
    public static NarrationLine gmSecret(String text) {
        return new NarrationLine(text, NarrationLineKind.GM_SECRET);
    }
}
