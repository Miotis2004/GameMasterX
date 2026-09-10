package com.gamemasterx.server.encounter.service;

import com.gamemasterx.server.encounter.idempotency.IdempotencyService;
import com.gamemasterx.server.encounter.model.EncounterDto;
import com.gamemasterx.server.exception.MongoTransactionUnavailableException;
import com.gamemasterx.server.diagnostics.MongoTransactionCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Coordinates the atomic commit of an encounter aggregate together with its
 * immutable turn and audit history.
 *
 * <p>This coordinator is the single enforcement point for three invariants that
 * a correct commit must satisfy:</p>
 *
 * <ul>
 *   <li><b>Idempotency</b> &ndash; when a caller supplies an idempotency key and
 *   that key has already completed a successful operation, the stored outcome is
 *   returned and the guarded operation is never run again. This is what prevents
 *   a retry from applying damage twice, consuming a resource twice, rolling the
 *   dice twice, or recording a turn twice.</li>
 *   <li><b>Transactional commit</b> &ndash; when the deployment is a
 *   transaction-capable replica set, the aggregate save and the turn/audit append
 *   run inside a single multi-document transaction, so they either both commit or
 *   neither does (no partial commit).</li>
 *   <li><b>Clear diagnostics when transactions are unavailable</b> &ndash; when
 *   the deployment cannot provide transactions and transactional commit is
 *   required, the commit is rejected with a clear diagnostic and no partial
 *   commit; otherwise a non-transactional fallback runs with a clearly recorded
 *   diagnostic.</li>
 * </ul>
 */
@Service
public class EncounterCommitCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(EncounterCommitCoordinator.class);

    private final EncounterCommitDelegate commitDelegate;
    private final IdempotencyService idempotencyService;
    private final MongoTransactionCapability transactionCapability;
    private final TransactionTemplate transactionTemplate;
    private final boolean requireTransactionalCommit;

    /**
     * Creates the coordinator.
     *
     * @param commitDelegate        performs the actual aggregate save and audit append
     * @param idempotencyService    the idempotency-key store
     * @param transactionCapability the MongoDB transaction capability detector
     * @param transactionTemplate   the transaction template bound to MongoDB
     * @param requireTransactionalCommit whether commits must be transactional
     */
    public EncounterCommitCoordinator(EncounterCommitDelegate commitDelegate,
                                      IdempotencyService idempotencyService,
                                      MongoTransactionCapability transactionCapability,
                                      TransactionTemplate transactionTemplate,
                                      @Value("${game.master.x.encounter.require-transactional-commit:true}")
                                              boolean requireTransactionalCommit) {
        this.commitDelegate = commitDelegate;
        this.idempotencyService = idempotencyService;
        this.transactionCapability = transactionCapability;
        this.transactionTemplate = transactionTemplate;
        this.requireTransactionalCommit = requireTransactionalCommit;
    }

    /**
     * Commits a guarded operation.
     *
     * @param idempotencyKey the caller-supplied idempotency key, or {@code null}
     * @param operation      a short label describing the guarded operation
     * @param supplier       performs the aggregate save and the turn/audit append;
     *                       must return the resulting encounter DTO
     * @return the commit outcome, including whether it was idempotent and
     *         transactional
     */
    public CommitOutcome commit(String idempotencyKey, String operation, Supplier<EncounterDto> supplier) {
        // 1. Idempotency short-circuit: a retry of a completed key returns the
        // original outcome without running the supplier at all.
        EncounterDto prior = idempotencyService.peekCompleted(idempotencyKey);
        if (prior != null) {
            return CommitOutcome.of(
                    prior, true, transactionCapability.isTransactionCapable(),
                    transactionCapability.transactionMode(), prior.getRevision(),
                    idempotencyKey, "Idempotent repeat of key '" + idempotencyKey + "'");
        }

        // A commit is made transactionally whenever the connected MongoDB can
        // provide multi-document transactions; this is what keeps the aggregate
        // save and the immutable turn/audit append atomic (criterion: transactional
        // commit is used whenever MongoDB supports it). The
        // requireTransactionalCommit flag does not gate this - it only decides
        // what happens when transactions are NOT available: reject outright (no
        // partial commit, clear diagnostic) or fall back to a flagged
        // non-transactional commit.
        if (transactionCapability.isTransactionCapable()) {
            return commitTransactional(idempotencyKey, operation, supplier);
        }

        // 2. Transactions are unavailable. When the deployment requires a
        // transactional commit, reject before any write so there is no partial
        // commit and a clear diagnostic is produced. Otherwise run a clearly
        // flagged non-transactional fallback.
        if (requireTransactionalCommit) {
            throw new MongoTransactionUnavailableException(
                    transactionCapability.isTransactionCapable(),
                    transactionCapability.isReplicaSetMember(),
                    transactionCapability.getReplicaSetName(),
                    transactionCapability.areLogicalSessionsSupported(),
                    transactionCapability.diagnosticDetails());
        }
        return commitNonTransactional(idempotencyKey, operation, supplier);
    }

    private CommitOutcome commitTransactional(String idempotencyKey, String operation,
                                              Supplier<EncounterDto> supplier) {
        EncounterDto result = transactionTemplate.execute(status -> {
            EncounterDto outcome = supplier.get();
            markCompleted(idempotencyKey, outcome);
            return outcome;
        });
        return CommitOutcome.of(result, false, true, "transactional", result.getRevision(),
                idempotencyKey, "Committed inside a multi-document transaction");
    }

    private CommitOutcome commitNonTransactional(String idempotencyKey, String operation,
                                                 Supplier<EncounterDto> supplier) {
        logger.warn("Transactional commit unavailable for '{}' (transactionMode={}); "
                        + "running with a non-transactional commit. Ensure MongoDB is a replica "
                        + "set to enable atomic commit of the encounter and its audit history.",
                operation, transactionCapability.transactionMode());
        EncounterDto result = supplier.get();
        markCompleted(idempotencyKey, result);
        return CommitOutcome.of(result, false, false, "non-transactional", result.getRevision(),
                idempotencyKey, "Committed without a multi-document transaction "
                        + "(transactionMode=" + transactionCapability.transactionMode() + ")");
    }

    private void markCompleted(String idempotencyKey, EncounterDto outcome) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        idempotencyService.complete(idempotencyKey, outcome.getId(), "encounter-commit", null, outcome);
    }

    /**
     * Callback that performs the actual aggregate save and the turn/audit append.
     * Implemented by {@link EncounterService}; kept as a separate type so the
     * coordinator stays focused on transaction and idempotency concerns.
     */
    @FunctionalInterface
    public interface EncounterCommitDelegate {
        /**
         * Saves the already-mutated encounter and appends the turn/audit history.
         *
         * @return the resulting encounter DTO
         */
        EncounterDto commit();
    }
}
