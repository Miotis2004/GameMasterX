package com.gamemasterx.server.exception;

import java.util.Map;

/**
 * Thrown when a multi-document commit cannot be made atomic because the running
 * MongoDB deployment cannot provide the required transaction capability.
 *
 * <p>This is raised only when the coordinator is configured to require
 * transactional commit (the {@code requireTransactionalCommit} flag) and the
 * deployment is not a transaction-capable replica set. Raising it <em>before</em>
 * any write guarantees <b>no partial commit</b>: nothing is persisted and a clear
 * diagnostic describing the missing capability is returned to the caller.</p>
 */
public class MongoTransactionUnavailableException extends RuntimeException {

    private static final String ERROR_CODE = "TRANSACTION_UNAVAILABLE";

    private final boolean transactionSupported;
    private final boolean replicaSetMember;
    private final String replicaSetName;
    private final boolean logicalSessionsSupported;
    private final Map<String, Object> diagnostics;

    /**
     * Creates the exception with a clear diagnostic describing why the required
     * transaction capability is unavailable.
     *
     * @param transactionSupported       whether the deployment supports transactions
     * @param replicaSetMember           whether the server is a replica set member
     * @param replicaSetName             the replica set name, or an empty string
     * @param logicalSessionsSupported   whether logical sessions are advertised
     * @param diagnostics                the full capability diagnostics
     */
    public MongoTransactionUnavailableException(boolean transactionSupported, boolean replicaSetMember,
                                                String replicaSetName, boolean logicalSessionsSupported,
                                                Map<String, Object> diagnostics) {
        super("The operation requires a transactional commit, but the connected MongoDB cannot provide "
                + "multi-document transactions (transactionSupported=" + transactionSupported + ", "
                + "replicaSetMember=" + replicaSetMember + ", replicaSetName='" + replicaSetName
                + "', logicalSessionsSupported=" + logicalSessionsSupported + "). "
                + "Run MongoDB as a replica set with a positive logical session timeout to enable "
                + "transactional commit, or relax the requireTransactionalCommit setting.");
        this.transactionSupported = transactionSupported;
        this.replicaSetMember = replicaSetMember;
        this.replicaSetName = replicaSetName;
        this.logicalSessionsSupported = logicalSessionsSupported;
        this.diagnostics = diagnostics;
    }

    /**
     * @return the error code, always {@code TRANSACTION_UNAVAILABLE}
     */
    public String getErrorCode() {
        return ERROR_CODE;
    }

    /**
     * @return {@code true} when the deployment advertises transaction support
     */
    public boolean isTransactionSupported() {
        return transactionSupported;
    }

    /**
     * @return {@code true} when the server is a replica set member
     */
    public boolean isReplicaSetMember() {
        return replicaSetMember;
    }

    /**
     * @return the replica set name, or an empty string
     */
    public String getReplicaSetName() {
        return replicaSetName;
    }

    /**
     * @return {@code true} when logical sessions are supported
     */
    public boolean isLogicalSessionsSupported() {
        return logicalSessionsSupported;
    }

    /**
     * @return the full capability diagnostics captured at the time of rejection
     */
    public Map<String, Object> getDiagnostics() {
        return diagnostics;
    }
}
