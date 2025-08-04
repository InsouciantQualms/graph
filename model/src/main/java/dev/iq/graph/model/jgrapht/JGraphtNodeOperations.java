/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.common.version.Versions;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.operations.NodeOperations;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jgrapht.Graph;

/**
 * JGraphT-based implementation of immutable node operations for versioned graph elements.
 * This class provides query-only operations on nodes. For mutation operations,
 * see JGraphtMutableNodeOperations.
 */
public final class JGraphtNodeOperations implements NodeOperations {

    private final Graph<Node, Edge> graph;

    public JGraphtNodeOperations(final Graph<Node, Edge> graph) {
        this.graph = graph;
    }

    public boolean contains(final Node node) {

        return graph.containsVertex(node);
    }

    public Set<Node> vertexSet() {

        return graph.vertexSet();
    }

    @Override
    public Optional<Node> findActive(final NanoId id) {
        return Versions.findActive(id, graph.vertexSet());
    }

    @Override
    public Optional<Node> findAt(final NanoId id, final Instant timestamp) {
        return Versions.findAt(id, timestamp, graph.vertexSet());
    }

    @Override
    public Node find(final Locator locator) {
        return graph.vertexSet().stream()
                .filter(n -> n.locator().equals(locator))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Node not found for locator: " + locator));
    }

    @Override
    public List<Node> findVersions(final NanoId id) {
        return Versions.findAllVersions(id, graph.vertexSet());
    }

    public List<Node> allActive() {
        return Versions.allActive(graph.vertexSet());
    }

    public Optional<Node> findNodeAt(final NanoId id, final Instant timestamp) {
        return findAt(id, timestamp);
    }

    public List<Node> activeNodes() {
        return allActive();
    }

    /**
     * Gets all neighbor nodes connected to the specified node via active edges.
     */
    public List<Node> getNeighbors(final Node node) {

        final var outgoingNeighbors = graph.outgoingEdgesOf(node).stream()
                .filter(edge -> edge.expired().isEmpty())
                .map(Edge::target);

        final var incomingNeighbors = graph.incomingEdgesOf(node).stream()
                .filter(edge -> edge.expired().isEmpty())
                .map(Edge::source);

        return Stream.concat(outgoingNeighbors, incomingNeighbors).toList();
    }

    @Override
    public Set<Edge> outgoingEdges(final Node node) {
        return graph.outgoingEdgesOf(node);
    }

    @Override
    public Set<Edge> incomingEdges(final Node node) {
        return graph.incomingEdgesOf(node);
    }

    @Override
    public Set<Edge> edges(final Node node) {
        return graph.edgesOf(node);
    }

    @Override
    public Set<Component> components(final Node node) {
        // TODO: Implement component tracking
        return Set.of();
    }

    @Override
    public Set<Node> neighbors(final Node node) {
        return Set.copyOf(getNeighbors(node));
    }
}
