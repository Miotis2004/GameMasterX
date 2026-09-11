package com.gamemasterx.server.gameplay.repository;

import com.gamemasterx.server.gameplay.model.Audit;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for the immutable, append-oriented
 * {@link Audit}
 * log.
 *
 * <p>
 * The audit log is append-only: the only write operation offered by this
 * repository is {@link #save} of a brand-new audit document. Existing audit
 * documents are never updated in place or deleted, so the full historical
 * record of accepted and rejected mutations can never be mutated or
 * retro-edited.
 * </p>
 *
 * <p>
 * Each entry carries a monotonically increasing {@link Audit#auditSequence()};
 * {@link #findFirstByOrderByAuditSequenceDesc()} lets the audit service assign
 * the next sequence number so new entries are appended in a stable, globally
 * increasing order.
 * </p>
 */
@Repository
public interface AuditRepository extends MongoRepository<Audit, String> {

    /**
     * @param encounterId the encounter identifier
     * @return the audit entries for the encounter, in append (sequence) order
     */
    List<Audit> findByEncounterIdOrderByAuditSequenceAsc(String encounterId);

    /**
     * @param campaignId the campaign identifier
     * @return the audit entries for the campaign, in append (sequence) order
     */
    List<Audit> findByCampaignIdOrderByAuditSequenceAsc(String campaignId);

    /**
     * @param turnId the turn identifier
     * @return the audit entries for the turn, in append (sequence) order
     */
    List<Audit> findByTurnIdOrderByAuditSequenceAsc(String turnId);

    /**
     * @return the highest-sequence audit entry currently stored, if any. Used by
     *         the audit service to assign the next monotonic sequence number when
     *         appending a new entry.
     */
    Optional<Audit> findFirstByOrderByAuditSequenceDesc();

    /**
     * @param encounterId the encounter identifier
     * @return the number of audit entries recorded for the encounter
     */
    long countByEncounterId(String encounterId);
}
