package dev.iq.graph.persistence.tinkerpop;

import dev.iq.common.fp.Io;
import dev.iq.common.persist.Session;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;

import java.util.Optional;

/**
 * Tinkerpop implementation of Session with transaction support.
 */
public final class TinkerpopSession implements Session {

    private final Graph graph;
    private final Optional<Transaction> transaction;

    public TinkerpopSession(final Graph graph) {

        this.graph = graph;
        // Try to create a transaction if the graph supports it
        Optional<Transaction> tx = Optional.empty();
        try {
            final var graphTx = graph.tx();
            graphTx.open();
            tx = Optional.of(graphTx);
        } catch (UnsupportedOperationException e) {
            // Graph doesn't support transactions - that's okay for TinkerGraph
            // We'll operate without explicit transaction management
        }
        this.transaction = tx;
    }

    @Override
    public void commit() {
        // Only commit if transaction is available
        transaction.ifPresent(Transaction::commit);
    }

    @Override
    public void rollback() {
        // Only rollback if transaction is available
        transaction.ifPresent(Transaction::rollback);
    }

    @Override
    public void close() {
        // Close transaction if it exists, then close the graph
        transaction.ifPresent(Transaction::close);
        Io.withVoid(graph::close);
    }

    Graph graph() {

        return graph;
    }
}