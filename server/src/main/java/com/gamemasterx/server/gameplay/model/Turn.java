package com.gamemasterx.server.gameplay.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable record capturing a single turn within an encounter.
 *
 * <p>A turn is the unit of turn history. It records which participant acted, in
 * which round and turn position, and embeds every {@link Action} taken during
 * the turn together with the {@link DiceResult}s they resolved. Crucially it
 * also records the encounter aggregate's <em>revision before</em> and
 * <em>revision after</em> the turn, so the exact optimistic-concurrency
 * generation of the encounter that each turn observed and produced is
 * preserved.</p>
 *
 * <p>The turn document is stored immutably in the {@code turns} collection: a
 * new {@code Turn} document is appended for every turn and the document is
 * never updated or deleted, so prior turn history can never be mutated.</p>
 */
@Document(collection = "turns")
public record Turn(
        /** Stable identifier of this turn document. */
        @Id
        String id,

        /** Identifier of the campaign this turn belongs to. */
        String campaignId,

        /** Identifier of the encounter this turn belongs to. */
        String encounterId,

        /** The round number within the encounter this turn is part of (>= 1). */
        int round,

        /** The zero-based position of this turn within its round. */
        int turnIndex,

        /** Identifier of the participant whose turn it was. */
        String actingParticipantId,

        /** The actions taken during this turn, in order. Unmodifiable. */
        List<Action> actions,

        /** The dice results resolved during this turn, in order. Unmodifiable. */
        List<DiceResult> diceResults,

        /**
         * The encounter aggregate revision immediately before this turn began.
         * Captures the "revision before" required by the audit model.
         */
        int revisionBefore,

        /**
         * The encounter aggregate revision immediately after this turn ended.
         * Captures the "revision after" required by the audit model.
         */
        int revisionAfter,

        /** Timestamp of when the turn began. */
        Instant startedAt,

        /** Timestamp of when the turn ended, or {@code null} if still in progress. */
        Instant endedAt) {

    /**
     * Convenience factory that normalises the embedded lists and computes the
     * turn duration when both timestamps are present.
     *
     * @param id                   stable identifier, or {@code null} when transient
     * @param campaignId           the owning campaign
     * @param encounterId          the owning encounter
     * @param round                the round number (>= 1)
     * @param turnIndex            the zero-based position within the round
     * @param actingParticipantId  the participant whose turn it was
     * @param actions              the actions taken (may be {@code null})
     * @param diceResults          the dice results resolved (may be {@code null})
     * @param revisionBefore       the encounter revision before the turn
     * @param revisionAfter        the encounter revision after the turn
     * @param startedAt            when the turn began
     * @param endedAt              when the turn ended, or {@code null}
     */
    public Turn(String id, String campaignId, String encounterId, int round, int turnIndex,
                String actingParticipantId, List<Action> actions, List<DiceResult> diceResults,
                int revisionBefore, int revisionAfter, Instant startedAt, Instant endedAt) {
        this.id = id;
        this.campaignId = campaignId;
        this.encounterId = encounterId;
        this.round = round;
        this.turnIndex = turnIndex;
        this.actingParticipantId = actingParticipantId;
        this.actions = (actions != null) ? List.copyOf(actions) : List.of();
        this.diceResults = (diceResults != null) ? List.copyOf(diceResults) : List.of();
        this.revisionBefore = revisionBefore;
        this.revisionAfter = revisionAfter;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    /**
     * @return a defensive copy of this turn with a newly assigned id, used when
     * materialising a transient turn as a persisted turn document
     */
    public Turn withId(String newId) {
        return new Turn(
                newId,
                campaignId,
                encounterId,
                round,
                turnIndex,
                actingParticipantId,
                actions,
                diceResults,
                revisionBefore,
                revisionAfter,
                startedAt,
                endedAt);
    }

    /**
     * @return an unmodifiable view of the actions taken during this turn
     */
    public List<Action> actions() {
        return Collections.unmodifiableList(actions);
    }

    /**
     * @return an unmodifiable view of the dice results resolved during this turn
     */
    public List<DiceResult> diceResults() {
        return Collections.unmodifiableList(diceResults);
    }

    /**
     * @return {@code true} when no actions were taken during this turn
     */
    public boolean isEmpty() {
        return actions.isEmpty();
    }

    /**
     * @return the duration of the turn, or {@code null} if the turn has not
     * ended yet (no {@link #endedAt})
     */
    public Duration duration() {
        if (startedAt == null || endedAt == null) {
            return null;
        }
        return Duration.between(startedAt, endedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Turn other)) return false;
        return round == other.round
                && turnIndex == other.turnIndex
                && revisionBefore == other.revisionBefore
                && revisionAfter == other.revisionAfter
                && Objects.equals(id, other.id)
                && Objects.equals(campaignId, other.campaignId)
                && Objects.equals(encounterId, other.encounterId)
                && Objects.equals(actingParticipantId, other.actingParticipantId)
                && Objects.equals(actions, other.actions)
                && Objects.equals(diceResults, other.diceResults)
                && Objects.equals(startedAt, other.startedAt)
                && Objects.equals(endedAt, other.endedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, campaignId, encounterId, round, turnIndex, actingParticipantId,
                actions, diceResults, revisionBefore, revisionAfter, startedAt, endedAt);
    }

    @Override
    public String toString() {
        return "Turn[" +
                "id=" + id +
                ", campaignId=" + campaignId +
                ", encounterId=" + encounterId +
                ", round=" + round +
                ", turnIndex=" + turnIndex +
                ", actingParticipantId=" + actingParticipantId +
                ", revisionBefore=" + revisionBefore +
                ", revisionAfter=" + revisionAfter + ']';
    }
}
