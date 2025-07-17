/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jgrapht.Graph;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.common.version.Versions;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Operations;
import dev.iq.graph.model.simple.SimpleEdge;

/**
 * JGraphT-based implementation of edge operations for versioned graph elements.
 */
public final class EdgeOperations implements Operations<Edge> {

    private final Graph<Node, Edge> graph;

    public EdgeOperations(final Graph<Node, Edge> graph) {

        this.graph = graph;
    }

    public Set<Edge> outgoingEdgesOf(final Node node) {

        return graph.outgoingEdgesOf(node);
    }

    public Set<Edge> incomingEdgesOf(final Node node) {

        return graph.incomingEdgesOf(node);
    }

    public Edge add(final Node source, final Node target, final Data data, final Instant timestamp) {
        final var locator = Locator.generate();
        final var edge = new SimpleEdge(locator, source, target, data, timestamp, Optional.empty());
        graph.addEdge(edge.source(), edge.target(), edge);
        return edge;
    }

    public Edge update(final NanoId id, final Data data, final Instant timestamp) {

        final var expired = expire(id, timestamp);
        final var incremented = expired.locator().increment();
        final var newEdge =
                new SimpleEdge(incremented, expired.source(), expired.target(), data, timestamp, Optional.empty());
        graph.addEdge(newEdge.source(), newEdge.target(), newEdge);
        return newEdge;
    }

    @Override
    public Optional<Edge> findActive(final NanoId id) {

        return Versions.findActive(id, graph.edgeSet());
    }

    @Override
    public Optional<Edge> findAt(final NanoId id, final Instant timestamp) {

        return Versions.findAt(id, timestamp, graph.edgeSet());
    }

    @Override
    public List<Edge> findAllVersions(final NanoId id) {

        return Versions.findAllVersions(id, graph.edgeSet());
    }

    @Override
    public List<Edge> allActive() {

        return Versions.allActive(graph.edgeSet());
    }

    @Override
    public Edge expire(final NanoId id, final Instant timestamp) {

        final var edge = OperationsHelper.validateForExpiry(findActive(id), id, "Edge");
        final var expiredEdge = new SimpleEdge(
                edge.locator(), edge.source(), edge.target(), edge.data(), edge.created(), Optional.of(timestamp));
        graph.removeEdge(edge);
        graph.addEdge(edge.source(), edge.target(), expiredEdge);
        return expiredEdge;
    }

    /**
     * Gets all active edges originating from the specified node.
     */
    public List<Edge> getEdgesFrom(final Node node) {

        return graph.outgoingEdgesOf(node).stream()
                .filter(edge -> edge.expired().isEmpty())
                .toList();
    }

    /**
     * Gets all active edges targeting the specified node.
     */
    public List<Edge> getEdgesTo(final Node node) {

        return graph.incomingEdgesOf(node).stream()
                .filter(edge -> edge.expired().isEmpty())
                .toList();
    }
}
