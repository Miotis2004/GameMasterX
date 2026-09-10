package com.gamemasterx.server.narration.config;

import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the AI narration context-assembly bounded
 * context.
 *
 * <p>Every value is safe to leave unset; sensible defaults keep the assembler
 * within the AI provider's context window. Each property is optionally
 * overridable through an environment variable using the
 * {@code GAME_MASTER_X_NARRATION_...} naming convention, so deployment
 * policy never has to be baked into the build.</p>
 *
 * <p>Bound from the prefix {@code game.master.x.narration}.</p>
 */
@ConfigurationProperties(prefix = "game.master.x.narration")
public class NarrationContextProperties {

    /**
     * The minimum membership role required to assemble a narration context.
     * The context is gated by backend authorization; any value below
     * {@link MembershipRole#PLAYER} would let a passive observer drive an AI
     * completion. Defaults to {@link MembershipRole#PLAYER}.
     */
    private String requiredRole = "PLAYER";

    /**
     * Hard cap, in UTF-8 bytes, on the player-visible context that is sent to
     * the AI. This is the value that is actually bounded; GM-only secrets are
     * kept in a separate partition and are never sent to the AI. Defaults to
     * {@value #DEFAULT_MAX_CONTEXT_BYTES} bytes, leaving headroom under the
     * default {@code 4096}-token AI context window.
     */
    private int maxContextBytes = DEFAULT_MAX_CONTEXT_BYTES;

    /**
     * Hard cap, in lines, on the player-visible context that is sent to the AI.
     * A secondary guard to {@link #maxContextBytes} so a very large number of
     * short lines is still bounded.
     */
    private int maxContextLines = DEFAULT_MAX_CONTEXT_LINES;

    /**
     * Number of recent turns drawn from the immutable audit history when
     * assembling the "recent narrative" of a session. Non-positive values
     * disable recent-narrative inclusion.
     */
    private int recentNarrativeTurns = DEFAULT_RECENT_TURNS;

    /**
     * Whether the assembled player-visible context may be truncated to respect
     * {@link #maxContextBytes}/{@link #maxContextLines}. When {@code false} an
     * over-budget context is rejected instead of truncated.
     */
    private boolean allowTruncation = true;

    /** Maximum number of world facts and objectives pulled from the adventure. */
    private int maxWorldFacts = DEFAULT_MAX_WORLD_FACTS;

    /** Maximum number of objectives pulled from the adventure. */
    private int maxObjectives = DEFAULT_MAX_OBJECTIVES;

    public static final int DEFAULT_MAX_CONTEXT_BYTES = 3072;
    public static final int DEFAULT_MAX_CONTEXT_LINES = 96;
    public static final int DEFAULT_RECENT_TURNS = 8;
    public static final int DEFAULT_MAX_WORLD_FACTS = 12;
    public static final int DEFAULT_MAX_OBJECTIVES = 12;

    public String getRequiredRole() {
        return requiredRole;
    }

    public void setRequiredRole(String requiredRole) {
        this.requiredRole = requiredRole;
    }

    public int getMaxContextBytes() {
        return maxContextBytes;
    }

    public void setMaxContextBytes(int maxContextBytes) {
        this.maxContextBytes = maxContextBytes;
    }

    public int getMaxContextLines() {
        return maxContextLines;
    }

    public void setMaxContextLines(int maxContextLines) {
        this.maxContextLines = maxContextLines;
    }

    public int getRecentNarrativeTurns() {
        return recentNarrativeTurns;
    }

    public void setRecentNarrativeTurns(int recentNarrativeTurns) {
        this.recentNarrativeTurns = recentNarrativeTurns;
    }

    public boolean isAllowTruncation() {
        return allowTruncation;
    }

    public void setAllowTruncation(boolean allowTruncation) {
        this.allowTruncation = allowTruncation;
    }

    public int getMaxWorldFacts() {
        return maxWorldFacts;
    }

    public void setMaxWorldFacts(int maxWorldFacts) {
        this.maxWorldFacts = maxWorldFacts;
    }

    public int getMaxObjectives() {
        return maxObjectives;
    }

    public void setMaxObjectives(int maxObjectives) {
        this.maxObjectives = maxObjectives;
    }

    /**
     * @return the configured minimum required role, resolved from the stored
     *         name (case-insensitive)
     * @throws IllegalArgumentException when the configured role name is unknown
     */
    public MembershipRole resolveRequiredRole() {
        return MembershipRole.parse(requiredRole);
    }
}
