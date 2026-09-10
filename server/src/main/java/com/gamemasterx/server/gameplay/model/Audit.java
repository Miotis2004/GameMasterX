package com.gamemasterx.server.gameplay.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An immutable record representing a single entry in the append-oriented audit
 * log.
 *
 * <p>Each {@code Audit} entry captures one audited event: the decision made on
 * a {@link Mutation} (accepted or rejected), the state <em>before</em> and
 * <em>after</em> the change, the mutation id, and the encounter aggregate
 * <em>revision before</em> and <em>revision after</em>. The entries form an
 * append-only log identified by a monotonically increasing {@link #auditSequence}
 * and are never updated or deleted once persisted, so prior history can never be
 * mutated.</p>
 *
 * <p>The log retains both accepted and rejected decisions.
 * {@link #acceptedMutations()} and {@link #rejectedMutations()} provide typed
 * access to each, and a batch of audit events can be filtered the same way so a
 * caller can reconstruct the full set of accepted and rejected mutations for a
 * turn or encounter.</p>
 */
@Document(collection = "audit_log")
public record Audit(
        /** Stable identifier of this audit document. */
        @Id
        String id,

        /**
         * Monotonic sequence number within the audit log. Assigned by the audit
         * service so entries are appended in a stable, globally increasing order
         * independent of the clock.
         */
        long auditSequence,

        /** Identifier of the campaign the audited event belongs to. */
        String campaignId,

        /** Identifier of the encounter the audited event belongs to. */
        String encounterId,

        /** Identifier of the turn the event belongs to, when applicable. */
        String turnId,

        /** Identifier of the action the event belongs to, when applicable. */
        String actionId,

        /** Identifier of the mutation that was audited, when applicable. */
        String mutationId,

        /** The kind of state that changed, for example {@code "hitPoints"}. */
        String subjectType,

        /** Identifier of the subject that changed. */
        String subjectId,

        /** The structured state before the change, serialised to JSON. */
        String before,

        /** The structured state after the change, serialised to JSON. */
        String after,

        /** The decision made on the mutation: {@link MutationDecision#ACCEPTED} or {@link MutationDecision#REJECTED}. */
        MutationDecision decision,

        /** Identifier of the actor (game master) who made the decision. */
        String actor,

        /** Free-form reason for a rejected decision, when applicable. */
        String reason,

        /** The encounter aggregate revision immediately before the audited change. */
        int revisionBefore,

        /** The encounter aggregate revision immediately after the audited change. */
        int revisionAfter,

        /** Timestamp of when the audit entry was recorded. */
        Instant recordedAt,

        /** Correlation id for distributed tracing, when available. */
        String correlationId) {

    /**
     * Convenience factory that defaults the recorded timestamp and normalises
     * optional fields.
     *
     * @param id             stable identifier, or {@code null} when transient
     * @param auditSequence  the monotonic position in the audit log
     * @param campaignId     the owning campaign; must not be blank
     * @param encounterId    the owning encounter; must not be blank
     * @param turnId         the owning turn, or {@code null}
     * @param actionId       the owning action, or {@code null}
     * @param mutationId     the audited mutation, or {@code null}
     * @param subjectType    the kind of state changed, or {@code null}
     * @param subjectId      the subject that changed, or {@code null}
     * @param before         the before state as JSON, or {@code null}
     * @param after          the after state as JSON, or {@code null}
     * @param decision       the decision made; must not be {@code null}
     * @param actor          the acting actor; must not be blank
     * @param reason         the rejection reason, or {@code null}
     * @param revisionBefore the encounter revision before the change
     * @param revisionAfter  the encounter revision after the change
     * @param recordedAt     the recording timestamp, or {@code Instant.now()}
     * @param correlationId  the correlation id, or {@code null}
     * @throws IllegalArgumentException if {@code campaignId}, {@code encounterId}
     * or {@code actor} is blank, or {@code decision} is {@code null}
     */
    public Audit(String id, long auditSequence, String campaignId, String encounterId, String turnId,
                 String actionId, String mutationId, String subjectType, String subjectId, String before,
                 String after, MutationDecision decision, String actor, String reason, int revisionBefore,
                 int revisionAfter, Instant recordedAt, String correlationId) {
        this.id = id;
        this.auditSequence = auditSequence;
        if (campaignId == null || campaignId.isBlank()) {
            throw new IllegalArgumentException("Audit campaignId must not be blank");
        }
        if (encounterId == null || encounterId.isBlank()) {
            throw new IllegalArgumentException("Audit encounterId must not be blank");
        }
        this.campaignId = campaignId;
        this.encounterId = encounterId;
        this.turnId = turnId;
        this.actionId = actionId;
        this.mutationId = mutationId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.before = before;
        this.after = after;
        this.decision = (decision != null) ? decision : MutationDecision.REJECTED;
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Audit actor must not be blank");
        }
        this.actor = actor;
        this.reason = reason;
        this.revisionBefore = revisionBefore;
        this.revisionAfter = revisionAfter;
        this.recordedAt = (recordedAt != null) ? recordedAt : Instant.now();
        this.correlationId = correlationId;
    }

    /**
     * Builds the audit entries for a single turn by recording the decision made
     * on each mutation considered during the turn. Accepted mutations are
     * applied; rejected ones are recorded with their reason.
     *
     * @param turnId           the turn being audited
     * @param campaignId       the owning campaign
     * @param encounterId      the owning encounter
     * @param revisionBefore   the encounter revision before the turn
     * @param revisionAfter    the encounter revision after the turn
     * @param actor            the acting actor
     * @param mutations        the mutations considered during the turn
     * @param resolutions      the decision applied to each corresponding mutation
     * @param reasons          the reason for each rejected mutation (may be {@code null})
     * @param correlationId    the correlation id, or {@code null}
     * @return the ordered audit entries for the turn
     */
    public static List<Audit> forTurn(String turnId, String campaignId, String encounterId,
                                      int revisionBefore, int revisionAfter, String actor,
                                      List<Mutation> mutations, List<MutationDecision> resolutions,
                                      List<String> reasons, String correlationId) {
        if (mutations == null) {
            return List.of();
        }
        List<String> resolvedReasons = reasons;
        List<Audit> entries = new java.util.ArrayList<>();
        for (int i = 0; i < mutations.size(); i++) {
            Mutation m = mutations.get(i);
            MutationDecision resolution = (resolutions != null && i < resolutions.size()
                    && resolutions.get(i) != null) ? resolutions.get(i) : MutationDecision.REJECTED;
            String reason = (resolvedReasons != null && i < resolvedReasons.size()
                    && resolvedReasons.get(i) != null) ? resolvedReasons.get(i) : null;
            entries.add(new Audit(
                    null,
                    0,
                    campaignId,
                    encounterId,
                    turnId,
                    null,
                    m.id(),
                    m.subjectType(),
                    m.subjectId(),
                    m.beforeJson(),
                    m.afterJson(),
                    resolution,
                    actor,
                    reason,
                    revisionBefore,
                    revisionAfter,
                    Instant.now(),
                    correlationId));
        }
        return entries;
    }

    /**
     * @return {@code true} when this entry records an accepted mutation
     */
    public boolean wasAccepted() {
        return decision == MutationDecision.ACCEPTED;
    }

    /**
     * @return {@code true} when this entry records a rejected mutation
     */
    public boolean wasRejected() {
        return decision == MutationDecision.REJECTED;
    }

    /**
     * Filters a batch of audit entries to those that accepted a mutation.
     *
     * @param entries the audit entries to filter
     * @return the accepted entries only
     */
    public static List<Audit> acceptedMutations(List<Audit> entries) {
        return filter(entries, Audit::wasAccepted);
    }

    /**
     * Filters a batch of audit entries to those that rejected a mutation.
     *
     * @param entries the audit entries to filter
     * @return the rejected entries only
     */
    public static List<Audit> rejectedMutations(List<Audit> entries) {
        return filter(entries, Audit::wasRejected);
    }

    private static List<Audit> filter(List<Audit> entries, java.util.function.Predicate<Audit> predicate) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream().filter(predicate).collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Audit other)) return false;
        return auditSequence == other.auditSequence
                && revisionBefore == other.revisionBefore
                && revisionAfter == other.revisionAfter
                && Objects.equals(id, other.id)
                && Objects.equals(campaignId, other.campaignId)
                && Objects.equals(encounterId, other.encounterId)
                && Objects.equals(turnId, other.turnId)
                && Objects.equals(actionId, other.actionId)
                && Objects.equals(mutationId, other.mutationId)
                && Objects.equals(subjectType, other.subjectType)
                && Objects.equals(subjectId, other.subjectId)
                && Objects.equals(before, other.before)
                && Objects.equals(after, other.after)
                && decision == other.decision
                && Objects.equals(actor, other.actor)
                && Objects.equals(reason, other.reason)
                && Objects.equals(recordedAt, other.recordedAt)
                && Objects.equals(correlationId, other.correlationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, auditSequence, campaignId, encounterId, turnId, actionId, mutationId,
                subjectType, subjectId, before, after, decision, actor, reason, revisionBefore,
                revisionAfter, recordedAt, correlationId);
    }

    @Override
    public String toString() {
        return "Audit[" +
                "id=" + id +
                ", auditSequence=" + auditSequence +
                ", encounterId=" + encounterId +
                ", turnId=" + turnId +
                ", mutationId=" + mutationId +
                ", decision=" + decision +
                ", actor=" + actor +
                ", revisionBefore=" + revisionBefore +
                ", revisionAfter=" + revisionAfter + ']';
    }
}
