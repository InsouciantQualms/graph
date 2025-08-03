/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Spring transaction manager for MongoDB.
 */
public final class MongoTransactionManager implements PlatformTransactionManager {

    private final MongoClient mongoClient;
    private static final String SESSION_KEY = "mongodb.client.session";

    public MongoTransactionManager(final MongoClient mongoClient) {

        this.mongoClient = mongoClient;
    }

    @Override
    public TransactionStatus getTransaction(final TransactionDefinition definition) {

        // Check if we already have an active transaction
        final var existingSession = (ClientSession) TransactionSynchronizationManager.getResource(SESSION_KEY);
        if (existingSession != null) {
            // Return existing transaction
            return new MongoTransactionStatus(existingSession, false);
        }

        // Start a new transaction
        final var session = mongoClient.startSession();
        session.startTransaction();

        // Store session in thread-local
        TransactionSynchronizationManager.bindResource(SESSION_KEY, session);

        return new MongoTransactionStatus(session, true);
    }

    @Override
    public void commit(final TransactionStatus status) {

        if ((status instanceof final MongoTransactionStatus txStatus) && txStatus.isNewTransaction()) {
            try {
                txStatus.getSession().commitTransaction();
            } finally {
                TransactionSynchronizationManager.unbindResource(SESSION_KEY);
                txStatus.getSession().close();
            }
        }
    }

    @Override
    public void rollback(final TransactionStatus status) {

        if ((status instanceof final MongoTransactionStatus txStatus) && txStatus.isNewTransaction()) {
            try {
                txStatus.getSession().abortTransaction();
            } finally {
                TransactionSynchronizationManager.unbindResource(SESSION_KEY);
                txStatus.getSession().close();
            }
        }
    }

    /**
     * Transaction status implementation for MongoDB.
     */
    private static final class MongoTransactionStatus extends AbstractTransactionStatus {

        private final ClientSession session;
        private final boolean newTransaction;

        MongoTransactionStatus(final ClientSession session, final boolean newTransaction) {

            this.session = session;
            this.newTransaction = newTransaction;
        }

        ClientSession getSession() {

            return session;
        }

        @Override
        public boolean isNewTransaction() {

            return newTransaction;
        }
    }
}
