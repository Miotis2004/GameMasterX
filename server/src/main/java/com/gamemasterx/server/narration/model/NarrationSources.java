package com.gamemasterx.server.narration.model;

/**
 * A summary of which sources contributed to an assembled
 * {@link NarrationContext} and how many lines each contributed.
 *
 * <p>This is diagnostic metadata: it records the provenance of the assembled
 * context (so a caller can see, for example, that the recent narrative or the
 * active scene was empty) without leaking any of the underlying data.</p>
 *
 * @param campaignLines         player-visible campaign lines contributed
 * @param sceneLines            player-visible scene lines contributed
 * @param encounterLines        player-visible encounter lines contributed
 * @param actingCharacterLines  player-visible acting-character lines contributed
 * @param visibleTargetLines    player-visible target lines contributed
 * @param recentNarrativeLines  player-visible recent-narrative lines contributed
 * @param worldFactLines        player-visible world-fact lines contributed
 * @param objectiveLines        player-visible objective lines contributed
 * @param gmSecretLines         GM-only secret lines contributed (never sent to AI)
 */
public record NarrationSources(
        int campaignLines,
        int sceneLines,
        int encounterLines,
        int actingCharacterLines,
        int visibleTargetLines,
        int recentNarrativeLines,
        int worldFactLines,
        int objectiveLines,
        int gmSecretLines) {

    /**
     * @return the total number of lines across every partition
     */
    public int totalLines() {
        return campaignLines + sceneLines + encounterLines + actingCharacterLines
                + visibleTargetLines + recentNarrativeLines + worldFactLines
                + objectiveLines + gmSecretLines;
    }
}
