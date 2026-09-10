package com.gamemasterx.server.narration.model;

import java.util.List;

/**
 * The assembled, authorization-checked context for a single AI narration
 * completion.
 *
 * <p>This record is the boundary of the narration bounded context. It is the
 * single artifact the rest of the application reasons about, and it encodes the
 * strict player-vs-GM separation as an invariant: the
 * {@link #playerVisibleContextLines() player-visible lines} are exactly the
 * lines that may be forwarded to the AI, and the
 * {@link #gmSecretContextLines() GM-only secret lines} are exactly the lines
 * that may not. A {@link #separationVerified()} flag records that the two
 * partitions were checked for overlap at assembly time.</p>
 *
 * <p>The two line lists are already bounded in size (see {@link
 * NarrationContextBudget}); the {@link #playerContextBytes()} and
 * {@link #playerContextBounded()} fields report the resulting size and whether
 * truncation was required.</p>
 *
 * @param instruction               the completion instruction driving the AI
 * @param playerVisibleContextLines bounded, player-visible lines sent to the AI
 * @param gmSecretContextLines      bounded, GM-only secret lines (never sent to AI)
 * @param sources                   per-source contribution summary
 * @param playerContextBytes        total UTF-8 size of the player-visible context
 * @param playerContextBounded      whether the player-visible context was truncated
 * @param boundNote                 truncation explanation, or {@code null}
 * @param separationVerified        whether overlap separation was verified
 */
public record NarrationContext(
        String instruction,
        List<String> playerVisibleContextLines,
        List<String> gmSecretContextLines,
        NarrationSources sources,
        int playerContextBytes,
        boolean playerContextBounded,
        String boundNote,
        boolean separationVerified) {

    public NarrationContext {
        playerVisibleContextLines = List.copyOf(playerVisibleContextLines);
        gmSecretContextLines = List.copyOf(gmSecretContextLines);
    }

    /**
     * @return an immutable copy of the player-visible context lines; these are
     *         the only lines that may be sent to the AI
     */
    public List<String> playerVisibleContextLines() {
        return playerVisibleContextLines;
    }

    /**
     * @return an immutable copy of the GM-only secret context lines. These are
     *         assembled and separated but must never be forwarded to the AI.
     */
    public List<String> gmSecretContextLines() {
        return gmSecretContextLines;
    }

    /**
     * @return the number of player-visible lines sent to the AI
     */
    public int playerVisibleLineCount() {
        return playerVisibleContextLines.size();
    }

    /**
     * @return the number of GM-only secret lines (kept internally, never sent
     *         to the AI)
     */
    public int gmSecretLineCount() {
        return gmSecretContextLines.size();
    }
}
