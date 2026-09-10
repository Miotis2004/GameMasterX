package com.gamemasterx.server.narration.model;

import java.util.List;

/**
 * Inputs identifying the sources the {@link
 * com.gamemasterx.server.narration.service.NarrationContextAssembler} pulls from
 * when assembling an AI narration context.
 *
 * <p>The assembler owns every source of truth: it resolves the encounter,
 * campaign, adventure, characters and recent turns from the identifiers here
 * (and, where a source is absent, degrades gracefully rather than failing).
 * Every field except the {@link #actor} is optional, so a caller may ask for as
 * much or as little context as the current session carries.</p>
 *
 * <p>Field semantics:</p>
 * <ul>
 *   <li>{@code campaignId} &ndash; the owning campaign. Loaded to supply the
 *       campaign identity and, through it, the selected adventure.</li>
 *   <li>{@code encounterId} &ndash; the active encounter. Loaded to supply the
 *       rules profile and the live encounter snapshot.</li>
 *   <li>{@code currentSceneId} &ndash; the scene in progress. When supplied and
 *       present in the selected adventure it is surfaced as the active scene.
 *       Otherwise the adventure's leading scene is used when available.</li>
 *   <li>{@code actingCharacterId} &ndash; the character whose turn it is (the
 *       "acting character"). Loaded to supply that character's sheet.</li>
 *   <li>{@code visibleTargetCharacterIds} &ndash; the characters the acting
 *       character can see/engage. Their visible state is surfaced as targets;
 *       their hidden tactical data is treated as a GM-only secret.</li>
 *   <li>{@code instruction} &ndash; the prompt driving the completion. When
 *       omitted or blank a neutral default is supplied.</li>
 *   <li>{@code actor} &ndash; the authenticated caller. Required; it is the
 *       backend-authorization gate for assembly.</li>
 * </ul>
 *
 * @param campaignId             the owning campaign, or {@code null}
 * @param encounterId            the active encounter, or {@code null}
 * @param currentSceneId         the in-progress scene, or {@code null}
 * @param actingCharacterId      the acting character, or {@code null}
 * @param visibleTargetCharacterIds the characters visible to the acting
 *                                 character (may be empty or {@code null})
 * @param instruction            the completion instruction, or {@code null}
 * @param actor                  the authenticated caller (required)
 */
public record NarrationInput(
        String campaignId,
        String encounterId,
        String currentSceneId,
        String actingCharacterId,
        List<String> visibleTargetCharacterIds,
        String instruction,
        String actor) {

    public NarrationInput {
        visibleTargetCharacterIds = (visibleTargetCharacterIds != null)
                ? List.copyOf(visibleTargetCharacterIds) : List.of();
    }

    /**
     * @param campaignId    the owning campaign
     * @param actor         the authenticated caller
     * @return an input that assembles a campaign-scoped context only
     */
    public static NarrationInput forCampaign(String campaignId, String actor) {
        return new NarrationInput(campaignId, null, null, null, null, null, actor);
    }

    /**
     * @param campaignId      the owning campaign
     * @param encounterId     the active encounter
     * @param instruction     the completion instruction
     * @param actor           the authenticated caller
     * @param actingCharacterId the acting character, or {@code null}
     * @return an input that assembles an encounter-scoped context
     */
    public static NarrationInput forEncounter(
            String campaignId,
            String encounterId,
            String instruction,
            String actor,
            String actingCharacterId) {
        return new NarrationInput(
                campaignId, encounterId, null, actingCharacterId, null, instruction, actor);
    }
}
