/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Spring transaction manager for SQLite using JDBI.
 */
public final class SqliteTransactionManager implements PlatformTransactionManager {

    private final Jdbi jdbi;
    private static final String HANDLE_KEY = "sqlite.jdbi.handle";

    public SqliteTransactionManager(final Jdbi jdbi) {

        this.jdbi = jdbi;
    }

    @Override
    public TransactionStatus getTransaction(final TransactionDefinition definition) {

        // Check if we already have an active transaction
        final var existingHandle = (Handle) TransactionSynchronizationManager.getResource(HANDLE_KEY);
        if (existingHandle != null) {
            // Return existing transaction
            return new SqliteTransactionStatus(existingHandle, false);
        }

        // Start a new transaction
        final var handle = jdbi.open();
        handle.begin();

        // Store handle in thread-local
        TransactionSynchronizationManager.bindResource(HANDLE_KEY, handle);

        return new SqliteTransactionStatus(handle, true);
    }

    @Override
    public void commit(final TransactionStatus status) {

        if ((status instanceof final SqliteTransactionStatus txStatus) && txStatus.isNewTransaction()) {
            try {
                txStatus.getHandle().commit();
            } finally {
                TransactionSynchronizationManager.unbindResource(HANDLE_KEY);
                txStatus.getHandle().close();
            }
        }
    }

    @Override
    public void rollback(final TransactionStatus status) {

        if ((status instanceof final SqliteTransactionStatus txStatus) && txStatus.isNewTransaction()) {
            try {
                txStatus.getHandle().rollback();
            } finally {
                TransactionSynchronizationManager.unbindResource(HANDLE_KEY);
                txStatus.getHandle().close();
            }
        }
    }

    /**
     * Transaction status implementation for SQLite.
     */
    private static final class SqliteTransactionStatus extends AbstractTransactionStatus {

        private final Handle handle;
        private final boolean newTransaction;

        SqliteTransactionStatus(final Handle handle, final boolean newTransaction) {

            this.handle = handle;
            this.newTransaction = newTransaction;
        }

        Handle getHandle() {

            return handle;
        }

        @Override
        public boolean isNewTransaction() {

            return newTransaction;
        }
    }
}
