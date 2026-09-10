package com.gamemasterx.server.inventory.model;

import com.gamemasterx.server.gameplay.model.MutationDecision;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An immutable record representing a single entry in the append-oriented
 * inventory audit log.
 *
 * <p>Each {@code InventoryAudit} entry captures one auditable change: the
 * decision made on an inventory mutation (accepted or rejected), the state
 * <em>before</em> and <em>after</em> the change, the subject that changed, the
 * acting actor and the aggregate <em>revision before</em> and <em>revision
 * after</em>. The entries form an append-only log identified by a monotonically
 * increasing {@link #auditSequence} and are never updated or deleted once
 * persisted, so prior history can never be retro-edited.</p>
 */
@Document(collection = "inventory_audit_log")
public record InventoryAudit(
        /** Stable identifier of this audit document. */
        @Id
        String id,

        /** Monotonic sequence number within the audit log. */
        long auditSequence,

        /** Identifier of the campaign the audited event belongs to. */
        String campaignId,

        /** Identifier of the inventory the audited event belongs to. */
        String inventoryId,

        /** The kind of state that changed, for example {@code "inventoryItem"} or {@code "consumableResource"}. */
        String subjectType,

        /** Identifier of the subject that changed. */
        String subjectId,

        /** The structured state before the change, serialised to JSON. */
        String before,

        /** The structured state after the change, serialised to JSON. */
        String after,

        /** The decision made on the mutation: {@link MutationDecision#ACCEPTED} or {@link MutationDecision#REJECTED}. */
        MutationDecision decision,

        /** Identifier of the actor who made the change. */
        String actor,

        /** Free-form reason for a rejected change, when applicable. */
        String reason,

        /** The inventory aggregate revision immediately before the audited change. */
        int revisionBefore,

        /** The inventory aggregate revision immediately after the audited change. */
        int revisionAfter,

        /** Timestamp of when the audit entry was recorded. */
        Instant recordedAt,

        /** Correlation id for distributed tracing, when available. */
        String correlationId) {

    /**
     * Convenience factory that defaults the recorded timestamp and normalises
     * optional fields.
     *
     * @param id            stable identifier, or {@code null} when transient
     * @param auditSequence the monotonic position in the audit log
     * @param campaignId    the owning campaign; must not be blank
     * @param inventoryId   the owning inventory; must not be blank
     * @param subjectType   the kind of state changed, or {@code null}
     * @param subjectId     the subject that changed, or {@code null}
     * @param before        the before state as JSON, or {@code null}
     * @param after         the after state as JSON, or {@code null}
     * @param decision      the decision made; must not be {@code null}
     * @param actor         the acting actor; must not be blank
     * @param reason        the rejection reason, or {@code null}
     * @param revisionBefore the inventory revision before the change
     * @param revisionAfter  the inventory revision after the change
     * @param recordedAt     the recording timestamp, or {@code Instant.now()}
     * @param correlationId  the correlation id, or {@code null}
     * @throws IllegalArgumentException if {@code campaignId}, {@code inventoryId}
     * or {@code actor} is blank, or {@code decision} is {@code null}
     */
    public InventoryAudit(String id, long auditSequence, String campaignId, String inventoryId,
                          String subjectType, String subjectId, String before, String after,
                          MutationDecision decision, String actor, String reason, int revisionBefore,
                          int revisionAfter, Instant recordedAt, String correlationId) {
        this.id = id;
        this.auditSequence = auditSequence;
        if (campaignId == null || campaignId.isBlank()) {
            throw new IllegalArgumentException("InventoryAudit campaignId must not be blank");
        }
        if (inventoryId == null || inventoryId.isBlank()) {
            throw new IllegalArgumentException("InventoryAudit inventoryId must not be blank");
        }
        this.campaignId = campaignId;
        this.inventoryId = inventoryId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.before = before;
        this.after = after;
        this.decision = (decision != null) ? decision : MutationDecision.REJECTED;
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("InventoryAudit actor must not be blank");
        }
        this.actor = actor;
        this.reason = reason;
        this.revisionBefore = revisionBefore;
        this.revisionAfter = revisionAfter;
        this.recordedAt = (recordedAt != null) ? recordedAt : Instant.now();
        this.correlationId = correlationId;
    }

    /**
     * @return {@code true} when this entry records an accepted change
     */
    public boolean wasAccepted() {
        return decision == MutationDecision.ACCEPTED;
    }

    /**
     * @return {@code true} when this entry records a rejected change
     */
    public boolean wasRejected() {
        return decision == MutationDecision.REJECTED;
    }

    /**
     * Filters a batch of audit entries to those that accepted a change.
     *
     * @param entries the audit entries to filter
     * @return the accepted entries only
     */
    public static List<InventoryAudit> acceptedChanges(List<InventoryAudit> entries) {
        return filter(entries, InventoryAudit::wasAccepted);
    }

    /**
     * Filters a batch of audit entries to those that rejected a change.
     *
     * @param entries the audit entries to filter
     * @return the rejected entries only
     */
    public static List<InventoryAudit> rejectedChanges(List<InventoryAudit> entries) {
        return filter(entries, InventoryAudit::wasRejected);
    }

    private static List<InventoryAudit> filter(List<InventoryAudit> entries,
                                               java.util.function.Predicate<InventoryAudit> predicate) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * @return a defensively-copied, unmodifiable list containing this entry
     */
    public List<InventoryAudit> asSingletonList() {
        return Collections.singletonList(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InventoryAudit other)) return false;
        return auditSequence == other.auditSequence
                && revisionBefore == other.revisionBefore
                && revisionAfter == other.revisionAfter
                && Objects.equals(id, other.id)
                && Objects.equals(campaignId, other.campaignId)
                && Objects.equals(inventoryId, other.inventoryId)
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
        return Objects.hash(id, auditSequence, campaignId, inventoryId, subjectType, subjectId, before,
                after, decision, actor, reason, revisionBefore, revisionAfter, recordedAt, correlationId);
    }

    @Override
    public String toString() {
        return "InventoryAudit[" +
                "id=" + id +
                ", auditSequence=" + auditSequence +
                ", inventoryId=" + inventoryId +
                ", subjectId=" + subjectId +
                ", decision=" + decision +
                ", actor=" + actor +
                ", revisionBefore=" + revisionBefore +
                ", revisionAfter=" + revisionAfter + ']';
    }
}
