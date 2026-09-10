package com.gamemasterx.server.gameplay.service;

import com.gamemasterx.server.gameplay.model.Audit;
import com.gamemasterx.server.gameplay.model.Mutation;
import com.gamemasterx.server.gameplay.model.MutationDecision;
import com.gamemasterx.server.gameplay.model.Turn;
import com.gamemasterx.server.gameplay.repository.AuditRepository;
import com.gamemasterx.server.gameplay.repository.TurnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service that owns the immutable, append-oriented storage of turn
 * and audit history.
 *
 * <p>This service is the single place where {@link Turn} and {@link Audit}
 * documents are persisted, and it enforces the append-only invariant: every
 * write is a brand-new {@code insert} of a document that has never existed
 * before. Documents are <b>never</b> updated in place or deleted, so prior turn
 * and audit history can never be mutated. This is what makes the history
 * immutable from the persistence layer's point of view.</p>
 *
 * <p>Turns are appended one document per turn; audit entries are appended one
 * document per audited event, each assigned the next monotonically increasing
 * {@link Audit#auditSequence()} so the log reads in a stable global order
 * regardless of wall-clock timing.</p>
 */
@Service
public class GameplayAuditService {

    /**
     * Appends a new turn document and then records the decision made on every
     * mutation considered during that turn. The turn's revision before and after
     * the turn are captured on both the {@link Turn} document and the resulting
     * {@link Audit} entries.
     *
     * @param turn         the turn to append; its {@code id} is assigned when missing
     * @param mutations    the mutations considered during the turn (may be empty or {@code null})
     * @param resolutions  the decision applied to each corresponding mutation (same size as {@code mutations})
     * @param reasons      a reason for each rejected mutation (same size as {@code mutations}, may be {@code null})
     * @param revisionBefore the encounter revision before the turn
     * @param revisionAfter  the encounter revision after the turn
     * @param actor        the acting actor who recorded the turn
     * @param correlationId the correlation id, or {@code null}
     * @return the appended turn document (with its assigned id)
     * @throws IllegalArgumentException if {@code resolutions} does not match the number of mutations
     */
    @Transactional
    public Turn appendTurnAndAudit(Turn turn, List<Mutation> mutations, List<MutationDecision> resolutions,
                                   List<String> reasons, int revisionBefore, int revisionAfter,
                                   String actor, String correlationId) {
        Turn savedTurn = appendTurn(turn);

        List<Mutation> resolved = (mutations != null) ? List.copyOf(mutations) : List.of();
        List<Audit> auditEntries = Audit.forTurn(
                savedTurn.id(),
                savedTurn.campaignId(),
                savedTurn.encounterId(),
                revisionBefore,
                revisionAfter,
                actor,
                resolved,
                resolutions,
                reasons,
                correlationId);
        if (!auditEntries.isEmpty()) {
            appendAuditEntries(auditEntries);
        }
        return savedTurn;
    }

    /**
     * Appends a single turn document. The document's id is assigned when
     * missing. The document is inserted, never updated.
     *
     * @param turn the turn to append; its {@code id} is assigned when missing
     * @return the appended turn document (with its assigned id)
     */
    @Transactional
    public Turn appendTurn(Turn turn) {
        if (turn == null) {
            throw new IllegalArgumentException("Turn must not be null");
        }
        if (turn.id() == null || turn.id().isBlank()) {
            turn = turn.withId(UUID.randomUUID().toString());
        }
        return turnRepository.save(turn);
    }

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
    public List<Audit> appendAuditEntries(List<Audit> entries) {
        List<Audit> resolved = (entries != null) ? new ArrayList<>(entries) : new ArrayList<>();
        if (resolved.isEmpty()) {
            return List.of();
        }
        long nextSequence = nextAuditSequence();
        List<Audit> stored = new ArrayList<>();
        for (Audit entry : resolved) {
            String id = (entry.id() != null && !entry.id().isBlank()) ? entry.id() : UUID.randomUUID().toString();
            stored.add(new Audit(
                    id,
                    nextSequence++,
                    entry.campaignId(),
                    entry.encounterId(),
                    entry.turnId(),
                    entry.actionId(),
                    entry.mutationId(),
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
     * @param encounterId the encounter identifier
     * @return the immutable turn history for the encounter, in round/turn order
     */
    @Transactional(readOnly = true)
    public List<Turn> turnsForEncounter(String encounterId) {
        return turnRepository.findByEncounterIdOrderByRoundAscTurnIndexAsc(encounterId);
    }

    /**
     * @param turnId the turn identifier
     * @return the immutable turn with the given id, if present
     */
    @Transactional(readOnly = true)
    public Optional<Turn> findTurn(String turnId) {
        return turnRepository.findById(turnId);
    }

    /**
     * @param encounterId the encounter identifier
     * @return the immutable audit log for the encounter, in append (sequence) order
     */
    @Transactional(readOnly = true)
    public List<Audit> auditLogForEncounter(String encounterId) {
        return auditRepository.findByEncounterIdOrderByAuditSequenceAsc(encounterId);
    }

    /**
     * @param turnId the turn identifier
     * @return the immutable audit entries for the turn, in append (sequence) order
     */
    @Transactional(readOnly = true)
    public List<Audit> auditLogForTurn(String turnId) {
        return auditRepository.findByTurnIdOrderByAuditSequenceAsc(turnId);
    }

    /**
     * @param campaignId the campaign identifier
     * @return the immutable audit log for the campaign, in append (sequence) order
     */
    @Transactional(readOnly = true)
    public List<Audit> auditLogForCampaign(String campaignId) {
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
        Long last = auditRepository.findByFirstByOrderByAuditSequenceDesc()
                .map(Audit::auditSequence)
                .orElse(-1L);
        return last + 1L;
    }

    private final TurnRepository turnRepository;
    private final AuditRepository auditRepository;

    /**
     * Creates the audit service with its repositories.
     */
    public GameplayAuditService(TurnRepository turnRepository, AuditRepository auditRepository) {
        this.turnRepository = turnRepository;
        this.auditRepository = auditRepository;
    }
}
