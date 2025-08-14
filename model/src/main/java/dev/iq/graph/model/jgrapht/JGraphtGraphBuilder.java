/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Locator;
import dev.iq.common.version.Uid;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.GraphBuilder;
import dev.iq.graph.model.GraphSpace;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.operations.EdgeOperations;
import dev.iq.graph.model.operations.NodeOperations;
import java.time.Instant;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.event.GraphEdgeChangeEvent;
import org.jgrapht.event.GraphListener;
import org.jgrapht.event.GraphVertexChangeEvent;
import org.jgrapht.graph.DirectedMultigraph;

/**
 * JGraphT implementation of GraphBuilder.
 * Allows controlled construction of graphs for testing purposes.
 */
public final class JGraphtGraphBuilder implements GraphBuilder {

    private final Graph<Node, Edge> graph;
    private final NodeOperations nodeOperations;
    private final EdgeOperations edgeOperations;
    private final JGraphtEdgeOperations jgraphtEdgeOperations;
    private final GraphListener<Node, Edge> listener;

    /**
     * Creates a new builder with a default no-op listener.
     */
    public JGraphtGraphBuilder() {
        this(new GraphListener<Node, Edge>() {
            @Override
            public void edgeAdded(GraphEdgeChangeEvent<Node, Edge> e) {}

            @Override
            public void edgeRemoved(GraphEdgeChangeEvent<Node, Edge> e) {}

            @Override
            public void vertexAdded(GraphVertexChangeEvent<Node> e) {}

            @Override
            public void vertexRemoved(GraphVertexChangeEvent<Node> e) {}
        });
    }

    /**
     * Creates a new builder with the specified listener.
     *
     * @param listener the graph listener to use
     */
    public JGraphtGraphBuilder(final GraphListener<Node, Edge> listener) {
        this.graph = new DirectedMultigraph<>(null, null, true); // Allow self-loops
        this.listener = listener;
        this.jgraphtEdgeOperations = new JGraphtEdgeOperations(graph);
        this.edgeOperations = jgraphtEdgeOperations;
        this.nodeOperations = new JGraphtNodeOperations(graph, jgraphtEdgeOperations);
    }

    @Override
    public Node addNode(final Uid id, final Type type, final Data data, final Instant timestamp) {
        return nodeOperations.add(id, type, data, timestamp);
    }

    @Override
    public Node updateNode(final Uid id, final Type type, final Data data, final Instant timestamp) {
        return nodeOperations.update(id, type, data, timestamp);
    }

    @Override
    public void expireNode(final Uid id, final Instant timestamp) {
        nodeOperations.expire(id, timestamp);
    }

    @Override
    public Edge addEdge(
            final Type type,
            final Node from,
            final Node to,
            final Data data,
            final Set<Locator> components,
            final Instant timestamp) {
        return edgeOperations.add(type, from, to, data, components, timestamp);
    }

    @Override
    public Edge updateEdge(
            final Uid id, final Type type, final Data data, final Set<Locator> components, final Instant timestamp) {
        return edgeOperations.update(id, type, data, components, timestamp);
    }

    @Override
    public void expireEdge(final Uid id, final Instant timestamp) {
        edgeOperations.expire(id, timestamp);
    }

    @Override
    public GraphSpace build() {
        // Create a GraphSpace with the pre-built graph
        return new JGraphtGraphSpace(graph, listener);
    }

    /**
     * Get the underlying node operations for direct access during testing.
     * @return the node operations
     */
    public NodeOperations nodeOperations() {
        return nodeOperations;
    }

    /**
     * Get the underlying edge operations for direct access during testing.
     * @return the edge operations
     */
    public EdgeOperations edgeOperations() {
        return edgeOperations;
    }

    /**
     * Get the underlying graph for direct access during testing.
     * @return the graph
     */
    public Graph<Node, Edge> graph() {
        return graph;
    }
}
