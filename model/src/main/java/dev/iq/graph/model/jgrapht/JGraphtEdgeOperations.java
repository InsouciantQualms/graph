/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Locator;
import dev.iq.common.version.Uid;
import dev.iq.common.version.Versions;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.operations.EdgeOperations;
import dev.iq.graph.model.simple.SimpleEdge;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.jgrapht.Graph;

/**
 * JGraphT implementation of mutable edge operations.
 * Used during graph construction and bulk operations.
 *
 * Edge operations are simpler than node operations as they don't require
 * cascading updates - when an edge is expired or updated, it doesn't
 * affect the nodes it connects.
 */
public final class JGraphtEdgeOperations implements EdgeOperations {

    /** Delegate graph. */
    private final Graph<Node, Edge> graph;

    /** Creates an operation instance backed by the specified delegate graph. */
    public JGraphtEdgeOperations(final Graph<Node, Edge> graph) {
        this.graph = graph;
    }

    /** {@inheritDoc} */
    @Override
    public Edge add(
            final Type type,
            final Node source,
            final Node target,
            final Data data,
            final Set<Locator> components,
            final Instant timestamp) {

        final var locator = Locator.generate();
        final var edge = new SimpleEdge(locator, type, source, target, data, components, timestamp, Optional.empty());
        graph.addEdge(edge.source(), edge.target(), edge);
        return edge;
    }

    /** {@inheritDoc} */
    @Override
    public Edge update(
            final Uid id, final Type type, final Data data, final Set<Locator> components, final Instant timestamp) {

        // First expire the existing edge
        final var existingEdge = JGraphtHelper.require(Versions.findActive(id, graph.edgeSet()), id, "Edge");
        final var expired = expireInternal(existingEdge, timestamp);

        // Then create a new version with incremented locator
        final var incremented = expired.locator().next();
        final var newEdge = new SimpleEdge(
                incremented, type, expired.source(), expired.target(), data, components, timestamp, Optional.empty());
        graph.addEdge(newEdge.source(), newEdge.target(), newEdge);
        return newEdge;
    }

    /** {@inheritDoc} */
    @Override
    public Edge expire(final Uid id, final Instant timestamp) {

        final var edge = JGraphtHelper.require(Versions.findActive(id, graph.edgeSet()), id, "Edge");
        return expireInternal(edge, timestamp);
    }

    /** Helper method to expire a resolved edge. */
    private Edge expireInternal(final Edge edge, final Instant timestamp) {

        // Create expired version of the edge
        final var expiredEdge = new SimpleEdge(
                edge.locator(),
                edge.type(),
                edge.source(),
                edge.target(),
                edge.data(),
                edge.components(),
                edge.created(),
                Optional.of(timestamp));

        // Remove the active edge and add the expired version
        graph.removeEdge(edge);
        graph.addEdge(edge.source(), edge.target(), expiredEdge);
        return expiredEdge;
    }
}
