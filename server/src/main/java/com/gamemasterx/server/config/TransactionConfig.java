package com.gamemasterx.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring transaction infrastructure for the MongoDB-backed aggregates.
 *
 * <p>GameMasterX commits its multi-document, append-oriented operations (an
 * aggregate save together with the immutable turn/audit history) through a
 * single {@link org.springframework.transaction.PlatformTransactionManager}.
 * Multi-document transactions are only available on a MongoDB replica set; on a
 * standalone server they cannot be started. This configuration registers a
 * {@link MongoTransactionManager} and a {@link TransactionTemplate} so that the
 * commit coordinator can use {@link org.springframework.transaction.support
 * .TransactionTemplate#execute} when the deployment is transaction-capable and
 * fall back to a clear, non-transactional path otherwise.</p>
 *
 * <p>Registering the manager unconditionally is safe: constructing a
 * {@link MongoTransactionManager} never connects to the server, and the
 * coordinator only invokes a transaction when {@link
 * com.gamemasterx.server.diagnostics.MongoTransactionCapability} reports that the
 * target replica set supports transactions.</p>
 */
@Configuration
public class TransactionConfig {

    /**
     * Registers the MongoDB transaction manager used by the commit coordinator.
     *
     * @param mongoDatabaseFactory the auto-configured MongoDB database factory
     *                           bound to the application's configured database
     * @return the MongoDB transaction manager
     */
    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory mongoDatabaseFactory) {
        return new MongoTransactionManager(mongoDatabaseFactory);
    }

    /**
     * Registers the transaction template used to wrap multi-document commits.
     *
     * @param manager the MongoDB transaction manager
     * @return the transaction template bound to the MongoDB transaction manager
     */
    @Bean
    public TransactionTemplate transactionTemplate(MongoTransactionManager manager) {
        return new TransactionTemplate(manager);
    }
}
