package com.gamemasterx.server.inventory.repository;

import com.gamemasterx.server.inventory.model.InventoryAudit;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for the immutable, append-oriented {@link
 * InventoryAudit} log.
 *
 * <p>The audit log is append-only: the only write operation offered by this
 * repository is {@link #save} of a brand-new audit document. Existing audit
 * documents are never updated in place or deleted, so the full historical
 * record of accepted and rejected inventory changes can never be mutated or
 * retro-edited.</p>
 *
 * <p>Each entry carries a monotonically increasing {@link
 * InventoryAudit#auditSequence()}; {@link #findFirstByOrderByAuditSequenceDesc()}
 * lets the audit service assign the next sequence number so new entries are
 * appended in a stable, globally increasing order.</p>
 */
@Repository
public interface InventoryAuditRepository extends MongoRepository<InventoryAudit, String> {

    /**
     * @param inventoryId the inventory identifier
     * @return the audit entries for the inventory, in append (sequence) order
     */
    List<InventoryAudit> findByInventoryIdOrderByAuditSequenceAsc(String inventoryId);

    /**
     * @param campaignId the campaign identifier
     * @return the audit entries for the campaign, in append (sequence) order
     */
    List<InventoryAudit> findByCampaignIdOrderByAuditSequenceAsc(String campaignId);

    /**
     * @return the highest-sequence audit entry currently stored, if any. Used by
     * the audit service to assign the next monotonic sequence number when
     * appending a new entry.
     */
    Optional<InventoryAudit> findFirstByOrderByAuditSequenceDesc();
}
