/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.tinkerpop;

import java.util.function.Supplier;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractTransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Spring transaction manager for Apache Tinkerpop graphs.
 */
public final class TinkerpopTransactionManager implements PlatformTransactionManager {

    private final Supplier<? extends Graph> graphSupplier;
    private static final String GRAPH_TRANSACTION_KEY = "tinkerpop.graph.transaction";

    public TinkerpopTransactionManager(final Supplier<? extends Graph> graphSupplier) {

        this.graphSupplier = graphSupplier;
    }

    @Override
    public TransactionStatus getTransaction(final TransactionDefinition definition) {

        final var graph = graphSupplier.get();

        // Check if graph supports transactions
        if (!graph.features().graph().supportsTransactions()) {
            // Return a simple status for graphs without transaction support
            return new SimpleTransactionStatus(false);
        }

        // Check if we already have an active transaction
        final var existingTransaction = TransactionSynchronizationManager.getResource(GRAPH_TRANSACTION_KEY);
        if (existingTransaction != null) {
            // Return existing transaction
            return new SimpleTransactionStatus(false);
        }

        // Start a new transaction
        final var tx = graph.tx();
        tx.open();

        // Store transaction in thread-local
        TransactionSynchronizationManager.bindResource(GRAPH_TRANSACTION_KEY, tx);

        return new TinkerpopTransactionStatus(tx);
    }

    @Override
    public void commit(final TransactionStatus status) {

        if ((status instanceof final TinkerpopTransactionStatus txStatus) && txStatus.isNewTransaction()) {
            try {
                txStatus.getTransaction().commit();
            } finally {
                TransactionSynchronizationManager.unbindResource(GRAPH_TRANSACTION_KEY);
            }
        }
    }

    @Override
    public void rollback(final TransactionStatus status) {

        if ((status instanceof final TinkerpopTransactionStatus txStatus) && txStatus.isNewTransaction()) {
            try {
                txStatus.getTransaction().rollback();
            } finally {
                TransactionSynchronizationManager.unbindResource(GRAPH_TRANSACTION_KEY);
            }
        }
    }

    /**
     * Transaction status implementation for Tinkerpop.
     */
    private static final class TinkerpopTransactionStatus extends AbstractTransactionStatus {

        private final Transaction transaction;
        private final boolean newTransaction;

        TinkerpopTransactionStatus(final Transaction transaction) {

            this.transaction = transaction;
            this.newTransaction = true;
        }

        Transaction getTransaction() {

            return transaction;
        }

        @Override
        public boolean isNewTransaction() {

            return newTransaction;
        }
    }
}
