package com.gamemasterx.server.diagnostics;

import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single authority for whether the running MongoDB deployment can provide the
 * multi-document transactions that GameMasterX requires for atomic commit.
 *
 * <p>MongoDB only supports multi-document transactions on a replica set (or a
 * sharded cluster), and only when the members expose a positive logical session
 * timeout. This component issues a {@code hello} command once and caches the
 * verdict so that every commit and every diagnostics read observes the same
 * decision.</p>
 *
 * <p>When {@link #isTransactionCapable()} is {@code false} the commit
 * coordinator cannot guarantee that an aggregate save and its immutable turn and
 * audit history are written together. In that case the operation either falls
 * back to a clearly-diagnosed non-transactional path or is rejected, depending on
 * the {@code requireTransactionalCommit} configuration, but in every case a clear
 * diagnostic is produced describing exactly why the required transaction
 * capability is unavailable.</p>
 */
@Component
public class MongoTransactionCapability {

    private static final String ADMIN_DATABASE = "admin";

    private final MongoClient mongoClient;

    private volatile boolean initialized;
    private volatile boolean replicaSetMember;
    private volatile String replicaSetName;
    private volatile boolean logicalSessionsSupported;
    private volatile boolean transactionCapable;
    private volatile long measuredAtMillis;

    /**
     * Creates the capability detector.
     *
     * @param mongoClient the shared MongoDB client
     */
    public MongoTransactionCapability(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    /**
     * @return {@code true} only when the connected MongoDB is a replica set
     * member with logical sessions enabled, and therefore supports multi-document
     * transactions
     */
    public synchronized boolean isTransactionCapable() {
        ensureInitialized();
        return transactionCapable;
    }

    /**
     * @return {@code true} when the server has been probed at least once
     */
    public synchronized boolean isInitialized() {
        ensureInitialized();
        return initialized;
    }

    /**
     * @return {@code true} when the connected MongoDB is a replica set member
     */
    public boolean isReplicaSetMember() {
        ensureInitialized();
        return replicaSetMember;
    }

    /**
     * @return the replica set name, or an empty string when not a member
     */
    public String getReplicaSetName() {
        ensureInitialized();
        return replicaSetName;
    }

    /**
     * @return {@code true} when the server advertises logical session support
     */
    public boolean areLogicalSessionsSupported() {
        ensureInitialized();
        return logicalSessionsSupported;
    }

    /**
     * @return the transaction mode in use: {@code "transactional"} when the
     * deployment supports transactions, otherwise {@code "non-transactional"}
     */
    public String transactionMode() {
        return isTransactionCapable() ? "transactional" : "non-transactional";
    }

    /**
     * Probes the server for the first time, caching the verdict. Subsequent
     * calls return the cached decision without any round-trip.
     */
    private synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        try {
            mongoClient.getDatabase(ADMIN_DATABASE).runCommand(new Document("ping", 1));
            Document hello = mongoClient.getDatabase(ADMIN_DATABASE).runCommand(new Document("hello", 1));
            replicaSetMember = hello.getBoolean("isReplicaSet", false);
            replicaSetName = hello.getString("setName");
            if (replicaSetName == null) {
                replicaSetName = "";
            }
            logicalSessionsSupported = hello.containsKey("logicalSessionTimeoutMinutes");
            Integer logicalSessionTimeoutMinutes = hello.getInteger("logicalSessionTimeoutMinutes", 0);
            // Transactions require a replica set with a positive logical session
            // timeout. A standalone server reports a zero timeout and no setName.
            transactionCapable = replicaSetMember && logicalSessionTimeoutMinutes != null
                    && logicalSessionTimeoutMinutes > 0;
        } catch (Exception unreachable) {
            // Leave the defaults (not a replica set, not capable) and record the
            // probe so diagnostics can report that the server was unreachable.
            replicaSetName = "";
            transactionCapable = false;
        } finally {
            initialized = true;
            measuredAtMillis = System.currentTimeMillis();
        }
    }

    /**
     * @return a diagnostics map describing the detected transaction capability,
     * including the fields the API and diagnostics endpoints surface
     */
    public Map<String, Object> diagnosticDetails() {
        ensureInitialized();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("initialized", initialized);
        details.put("replicaSetMember", replicaSetMember);
        details.put("replicaSetName", replicaSetName);
        details.put("logicalSessionsSupported", logicalSessionsSupported);
        details.put("transactionSupported", transactionCapable);
        details.put("transactionMode", transactionMode());
        details.put("measuredAt", Instant.ofEpochMilli(measuredAtMillis).toString());
        return details;
    }
}
