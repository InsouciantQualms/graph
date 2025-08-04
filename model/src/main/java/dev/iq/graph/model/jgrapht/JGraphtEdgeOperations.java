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
import dev.iq.graph.model.operations.EdgeOperations;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jgrapht.Graph;

/**
 * JGraphT-based implementation of immutable edge operations for versioned graph elements.
 * This class provides query-only operations on edges. For mutation operations,
 * see JGraphtMutableEdgeOperations.
 */
public final class JGraphtEdgeOperations implements EdgeOperations {

    private final Graph<Node, Edge> graph;

    public JGraphtEdgeOperations(final Graph<Node, Edge> graph) {

        this.graph = graph;
    }

    public Set<Edge> outgoingEdgesOf(final Node node) {

        return graph.outgoingEdgesOf(node);
    }

    public Set<Edge> incomingEdgesOf(final Node node) {

        return graph.incomingEdgesOf(node);
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
    public Edge find(final Locator locator) {

        return graph.edgeSet().stream()
                .filter(e -> e.locator().equals(locator))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Edge not found for locator: " + locator));
    }

    @Override
    public List<Edge> findVersions(final NanoId id) {

        return Versions.findAllVersions(id, graph.edgeSet());
    }

    public List<Edge> allActive() {

        return Versions.allActive(graph.edgeSet());
    }

    @Override
    public Node source(final Edge edge) {
        return graph.getEdgeSource(edge);
    }

    @Override
    public Node target(final Edge edge) {
        return graph.getEdgeTarget(edge);
    }

    @Override
    public Set<Component> components(final Edge edge) {
        // TODO: Implement component tracking
        return Set.of();
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
