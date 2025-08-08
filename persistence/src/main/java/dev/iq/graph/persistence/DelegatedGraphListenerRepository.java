/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */
package dev.iq.graph.persistence;

import dev.iq.common.annotation.Stable;
import dev.iq.common.fp.Io;
import dev.iq.common.fp.Proc0;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import org.jgrapht.event.GraphEdgeChangeEvent;
import org.jgrapht.event.GraphVertexChangeEvent;
import org.springframework.stereotype.Repository;

/**
 * GraphSpace listener that queues up database operations in response to graph events and then
 * executes then defers execution until the flush() method is called.
 */
@Repository("delegatedGraphListenerRepository")
@Stable
public final class DelegatedGraphListenerRepository implements GraphListenerRepository {

    private final Collection<Proc0> operations = new ArrayList<>();

    private final GraphRepository delegate;

    public DelegatedGraphListenerRepository(final GraphRepository delegate) {

        this.delegate = delegate;
    }

    @Override
    public void vertexAdded(final GraphVertexChangeEvent<Node> event) {

        final var vertex = event.getVertex();
        operations.add(() -> delegate.nodes().save(vertex));
    }

    @Override
    public void vertexRemoved(final GraphVertexChangeEvent<Node> event) {

        final var vertex = event.getVertex();
        final var expiredTime = vertex.expired().orElseGet(Instant::now);
        operations.add(() -> delegate.nodes().expire(vertex.locator().id(), expiredTime));
    }

    @Override
    public void edgeAdded(final GraphEdgeChangeEvent<Node, Edge> event) {

        final var edge = event.getEdge();
        operations.add(() -> delegate.edges().save(edge));
    }

    @Override
    public void edgeRemoved(final GraphEdgeChangeEvent<Node, Edge> event) {

        final var edge = event.getEdge();
        final var expiredTime = edge.expired().orElseGet(Instant::now);
        operations.add(() -> delegate.edges().expire(edge.locator().id(), expiredTime));
    }

    @Override
    public void flush() {

        Io.withVoid(() -> {
            operations.forEach(Io::withVoid);
            operations.clear();
        });
    }
}
