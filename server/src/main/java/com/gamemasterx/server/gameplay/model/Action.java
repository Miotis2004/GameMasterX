package com.gamemasterx.server.gameplay.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable record capturing a single gameplay {@link Action}.
 *
 * <p>An action is the fundamental unit of gameplay that the audit log records.
 * It captures everything required to reconstruct and verify what happened:
 * <ul>
 *   <li><b>Inputs</b> &ndash; who acted ({@link #actorId}), what was targeted
 *       ({@link #targetId}/{@link #targetName}) and what skill or ability was
 *       used ({@link #skillOrAbility}), plus the typed {@link #type}.</li>
 *   <li><b>Modifiers</b> &ndash; the ordered {@link #modifiers} list naming each
 *       bonus/penalty and its signed value.</li>
 *   <li><b>Random values</b> &ndash; the resolved {@link #diceResults} that
 *       contributed to the action.</li>
 *   <li><b>Final outcome</b> &ndash; the aggregate {@link #modifierTotal}, the
 *       {@link #total} (the action's net result) and the {@link #outcome}.</li>
 * </ul>
 *
 * <p>As a Java record every field is final, so an {@code Action} cannot be
 * mutated after construction. It is embedded inside a {@link Turn} and mirrored
 * verbatim into the audit log.</p>
 */
public record Action(
        /** Stable identifier, or {@code null} when the action is still transient. */
        String id,

        /** The typed category of this action. */
        ActionType type,

        /** Identifier of the actor (participant, NPC or player) who performed the action. */
        String actorId,

        /** Identifier of the targeted participant, when applicable. */
        String targetId,

        /** Human-readable name of the target, when applicable. */
        String targetName,

        /** The skill or ability the action was based on, when applicable. */
        String skillOrAbility,

        /** The named, signed modifiers applied to the action. Unmodifiable. */
        List<Modifier> modifiers,

        /** The dice results resolved as part of this action. Unmodifiable. */
        List<DiceResult> diceResults,

        /** The sum of every {@link Modifier#value()} in {@link #modifiers}. */
        int modifierTotal,

        /** The net result of the action: the outcome die total plus all modifiers. */
        int total,

        /** The resolved {@link ActionOutcome} of the action. */
        ActionOutcome outcome,

        /** Free-form note describing the action, when applicable. */
        String note,

        /** Timestamp of when the action was resolved, or {@code null} when transient. */
        Instant performedAt) {

    /**
     * Convenience factory that computes {@link #modifierTotal} from the given
     * modifiers and normalises the (possibly {@code null}) lists.
     *
     * @param id           stable identifier, or {@code null}
     * @param type         the typed category of the action
     * @param actorId      who performed the action
     * @param targetId     the targeted participant id, or {@code null}
     * @param targetName   the target's name, or {@code null}
     * @param skillOrAbility the skill or ability used, or {@code null}
     * @param modifiers    the applied modifiers (may be {@code null})
     * @param diceResults  the resolved dice results (may be {@code null})
     * @param total        the net result of the action
     * @param outcome      the resolved outcome
     * @param note         a free-form note, or {@code null}
     * @param performedAt  the time the action was performed, or {@code null}
     */
    public Action(String id, ActionType type, String actorId, String targetId, String targetName,
                  String skillOrAbility, List<Modifier> modifiers, List<DiceResult> diceResults,
                  int modifierTotal, int total, ActionOutcome outcome, String note, Instant performedAt) {
        this.id = id;
        this.type = (type != null) ? type : ActionType.OTHER;
        this.actorId = actorId;
        this.targetId = targetId;
        this.targetName = targetName;
        this.skillOrAbility = skillOrAbility;
        this.modifiers = normalize(modifiers);
        this.diceResults = normalize(diceResults);
        this.modifierTotal = computeModifierTotal(this.modifiers);
        this.total = total;
        this.outcome = (outcome != null) ? outcome : ActionOutcome.UNKNOWN;
        this.note = note;
        this.performedAt = performedAt;
    }

    /**
     * @return an unmodifiable view of this action's modifiers
     */
    public List<Modifier> modifiers() {
        return Collections.unmodifiableList(modifiers);
    }

    /**
     * @return an unmodifiable view of this action's resolved dice results
     */
    public List<DiceResult> diceResults() {
        return Collections.unmodifiableList(diceResults);
    }

    /**
     * @return {@code true} when this action resolved no dice
     */
    public boolean hasNoDice() {
        return diceResults.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Action other)) return false;
        return modifierTotal == other.modifierTotal
                && total == other.total
                && Objects.equals(id, other.id)
                && type == other.type
                && Objects.equals(actorId, other.actorId)
                && Objects.equals(targetId, other.targetId)
                && Objects.equals(targetName, other.targetName)
                && Objects.equals(skillOrAbility, other.skillOrAbility)
                && Objects.equals(modifiers, other.modifiers)
                && Objects.equals(diceResults, other.diceResults)
                && outcome == other.outcome
                && Objects.equals(note, other.note)
                && Objects.equals(performedAt, other.performedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, actorId, targetId, targetName, skillOrAbility,
                modifiers, diceResults, modifierTotal, total, outcome, note, performedAt);
    }

    @Override
    public String toString() {
        return "Action[" +
                "id=" + id +
                ", type=" + type +
                ", actorId=" + actorId +
                ", targetId=" + targetId +
                ", modifiers=" + modifiers +
                ", total=" + total +
                ", outcome=" + outcome + ']';
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> normalize(List<T> values) {
        return (values != null) ? List.copyOf(values) : List.of();
    }

    private static int computeModifierTotal(List<Modifier> modifiers) {
        int sum = 0;
        for (Modifier m : modifiers) {
            sum += m.value();
        }
        return sum;
    }
}
