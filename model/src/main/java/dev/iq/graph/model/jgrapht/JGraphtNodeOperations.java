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
import dev.iq.graph.model.operations.NodeOperations;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleNode;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.jgrapht.Graph;

/**
 * JGraphT implementation of mutable node operations.
 * Used during graph construction and bulk operations.
 *
 * This implementation ensures referential integrity:
 * - When a node is expired, all connected edges are also expired
 * - When a node is updated, connected edges are recreated to point to the new version
 */
public final class JGraphtNodeOperations implements NodeOperations {

    /** Graph delegate. */
    private final Graph<Node, Edge> graph;
    /** Edge operations to perform. */
    private final JGraphtEdgeOperations edgeOperations;

    /** Creates a new node operations. */
    public JGraphtNodeOperations(final Graph<Node, Edge> graph, final JGraphtEdgeOperations edgeOperations) {
        this.graph = graph;
        this.edgeOperations = edgeOperations;
    }

    /** {@inheritDoc} */
    @Override
    public Node add(final Type type, final Data data, final Instant timestamp) {

        final var locator = Locator.generate();
        final var node = new SimpleNode(locator, type, data, timestamp, Optional.empty());
        graph.addVertex(node);
        return node;
    }

    /** {@inheritDoc} */
    @Override
    public Node update(final Uid id, final Type type, final Data data, final Instant timestamp) {

        final var existing = JGraphtHelper.require(Versions.findActive(id, graph.vertexSet()), id, "Node");

        // Collect edge information before expiring
        final var incoming = graph.incomingEdgesOf(existing);
        final var outgoing = graph.outgoingEdgesOf(existing);

        // Expire the existing node (which also expires its edges)
        final var expired = expire(id, timestamp);

        // Create new version
        final var incremented = expired.locator().next();
        final var newNode = new SimpleNode(incremented, type, data, timestamp, Optional.empty());
        graph.addVertex(newNode);

        // Recreate edges to the new node
        recreateEdges(newNode, incoming, outgoing, newNode.created(), Optional.empty());
        return newNode;
    }

    /** {@inheritDoc} */
    @Override
    public Node expire(final Uid id, final Instant timestamp) {

        final var node = JGraphtHelper.require(Versions.findActive(id, graph.vertexSet()), id, "Node");

        // Collect all connected edges
        final var incoming = graph.incomingEdgesOf(node);
        final var outgoing = graph.outgoingEdgesOf(node);

        // Expire active edges to maintain referential integrity
        expireActiveEdges(incoming, timestamp);
        expireActiveEdges(outgoing, timestamp);

        // Create expired node
        final var expiredNode = node.expire(timestamp);

        // Remove old node and add expired version
        graph.removeVertex(node);
        graph.addVertex(expiredNode);

        // Recreate all edges with updated endpoints (pointing to the expired node)
        recreateEdges(expiredNode, incoming, outgoing, expiredNode.created(), expiredNode.expired());
        return expiredNode;
    }

    /**
     * Expires all active edges in the collection.
     */
    private void expireActiveEdges(final Collection<Edge> edges, final Instant timestamp) {

        edges.stream()
                .filter(edge -> edge.expired().isEmpty())
                .forEach(edge -> edgeOperations.expire(edge.locator().id(), timestamp));
    }

    /**
     * Recreates edges for a new node version after update.
     */
    private void recreateEdges(
            final Node node,
            final Iterable<Edge> incoming,
            final Iterable<Edge> outgoing,
            final Instant created,
            final Optional<Instant> expired) {

        incoming.forEach(e ->
                recreateEdge(e, e.source(), node, created, expired)
        );
        outgoing.forEach(e ->
                recreateEdge(e, node, e.target(), created, expired)
        );
    }

    private void recreateEdge(
            final Edge existing,
            final Node source,
            final Node target,
            final Instant created,
            final Optional<Instant> expired
    ) {

        final var added = new SimpleEdge(
                existing.locator(),
                existing.type(),
                source,
                target,
                existing.data(),
                existing.components(),
                created,
                expired
        );
        graph.addEdge(source, target, added);
    }
}
