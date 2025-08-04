/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.graph.model.Edge;
import dev.iq.graph.model.GraphView;
import dev.iq.graph.model.Node;
import java.util.HashSet;
import java.util.Set;
import org.jgrapht.Graph;

/**
 * JGraphT implementation of GraphView that provides a filtered view of a graph.
 */
public final class JGraphtGraphView implements GraphView {

    private final Graph<Node, Edge> graph;
    private final Set<Node> nodeFilter;
    private final Set<Edge> edgeFilter;

    /**
     * Creates a view of the entire graph.
     */
    public JGraphtGraphView(final Graph<Node, Edge> graph) {
        this.graph = graph;
        this.nodeFilter = null;
        this.edgeFilter = null;
    }

    /**
     * Creates a filtered view of the graph with only the specified nodes and edges.
     */
    public JGraphtGraphView(final Graph<Node, Edge> graph, final Set<Node> nodes, final Set<Edge> edges) {
        this.graph = graph;
        this.nodeFilter = new HashSet<>(nodes);
        this.edgeFilter = new HashSet<>(edges);
    }

    @Override
    public Set<Node> nodes() {
        return (nodeFilter != null) ? new HashSet<>(nodeFilter) : new HashSet<>(graph.vertexSet());
    }

    @Override
    public Set<Edge> edges() {
        return (edgeFilter != null) ? new HashSet<>(edgeFilter) : new HashSet<>(graph.edgeSet());
    }

    @Override
    public Set<Edge> outgoingEdges(final Node node) {
        final var edges = graph.outgoingEdgesOf(node);
        if (edgeFilter != null) {
            final var filtered = new HashSet<>(edges);
            filtered.retainAll(edgeFilter);
            return filtered;
        }
        return new HashSet<>(edges);
    }

    @Override
    public Set<Edge> incomingEdges(final Node node) {
        final var edges = graph.incomingEdgesOf(node);
        if (edgeFilter != null) {
            final var filtered = new HashSet<>(edges);
            filtered.retainAll(edgeFilter);
            return filtered;
        }
        return new HashSet<>(edges);
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
    public boolean contains(final Node node) {
        return (nodeFilter != null) ? nodeFilter.contains(node) : graph.containsVertex(node);
    }

    @Override
    public boolean contains(final Edge edge) {
        return (edgeFilter != null) ? edgeFilter.contains(edge) : graph.containsEdge(edge);
    }
}
