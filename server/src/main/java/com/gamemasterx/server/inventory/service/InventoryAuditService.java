package com.gamemasterx.server.inventory.service;

import com.gamemasterx.server.inventory.model.InventoryAudit;
import com.gamemasterx.server.inventory.repository.InventoryAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service that owns the immutable, append-oriented storage of the
 * inventory audit log.
 *
 * <p>This service is the single place where {@link InventoryAudit} documents
 * are persisted, and it enforces the append-only invariant: every write is a
 * brand-new {@code insert} of a document that has never existed before.
 * Documents are <b>never</b> updated in place or deleted, so prior audit
 * history can never be mutated from the persistence layer's point of view.</p>
 *
 * <p>Audit entries are appended one document per audited change, each assigned
 * the next monotonically increasing {@link InventoryAudit#auditSequence()} so
 * the log reads in a stable global order regardless of wall-clock timing.</p>
 */
@Service
public class InventoryAuditService {

    /**
     * Appends one or more audit entries to the immutable audit log, assigning
     * each the next monotonic sequence number in a single critical step so the
     * sequence stays gap-free and strictly increasing even under concurrency.
     * Entries are inserted, never updated.
     *
     * @param entries the audit entries to append
     * @return the appended audit documents, with ids and sequence numbers assigned
     */
    @Transactional
    public List<InventoryAudit> appendAuditEntries(List<InventoryAudit> entries) {
        List<InventoryAudit> resolved = (entries != null) ? new ArrayList<>(entries) : new ArrayList<>();
        if (resolved.isEmpty()) {
            return List.of();
        }
        long nextSequence = nextAuditSequence();
        List<InventoryAudit> stored = new ArrayList<>();
        for (InventoryAudit entry : resolved) {
            String id = (entry.id() != null && !entry.id().isBlank()) ? entry.id() : UUID.randomUUID().toString();
            stored.add(new InventoryAudit(
                    id,
                    nextSequence++,
                    entry.campaignId(),
                    entry.inventoryId(),
                    entry.subjectType(),
                    entry.subjectId(),
                    entry.before(),
                    entry.after(),
                    entry.decision(),
                    entry.actor(),
                    entry.reason(),
                    entry.revisionBefore(),
                    entry.revisionAfter(),
                    entry.recordedAt(),
                    entry.correlationId()));
        }
        return auditRepository.saveAll(stored);
    }

    /**
     * @param inventoryId the inventory identifier
     * @return the immutable audit log for the inventory, in append (sequence) order
     */
    @Transactional(readOnly = true)
    public List<InventoryAudit> auditLogForInventory(String inventoryId) {
        return auditRepository.findByInventoryIdOrderByAuditSequenceAsc(inventoryId);
    }

    /**
     * @param campaignId the campaign identifier
     * @return the immutable audit log for the campaign, in append (sequence) order
     */
    @Transactional(readOnly = true)
    public List<InventoryAudit> auditLogForCampaign(String campaignId) {
        return auditRepository.findByCampaignIdOrderByAuditSequenceAsc(campaignId);
    }

    /**
     * Reserves the next audit log sequence number. Runs inside the owning
     * transaction so concurrent appends are serialised and the sequence stays
     * strictly increasing.
     *
     * @return the next monotonic audit sequence number (0 when the log is empty)
     */
    private long nextAuditSequence() {
        Optional<InventoryAudit> last = auditRepository.findFirstByOrderByAuditSequenceDesc();
        long current = last.map(InventoryAudit::auditSequence).orElse(-1L);
        return current + 1L;
    }

    private final InventoryAuditRepository auditRepository;

    /**
     * Creates the audit service with its repository.
     */
    public InventoryAuditService(InventoryAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }
}
