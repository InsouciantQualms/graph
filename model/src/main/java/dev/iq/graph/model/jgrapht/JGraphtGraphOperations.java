/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.graph.model.Component;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.GraphBuilder;
import dev.iq.graph.model.GraphView;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.operations.ComponentOperations;
import dev.iq.graph.model.operations.EdgeOperations;
import dev.iq.graph.model.operations.NodeOperations;
import dev.iq.graph.model.operations.PathOperations;
import java.util.List;
import java.util.Set;
import org.jgrapht.ListenableGraph;
import org.jgrapht.event.GraphListener;
import org.jgrapht.graph.DefaultListenableGraph;
import org.jgrapht.graph.DirectedMultigraph;

/**
 * JGraphT implementation of Graph interface.
 * Serves as a container for all graph elements and operations.
 */
public final class JGraphtGraphOperations implements dev.iq.graph.model.Graph {

    /** JGraphT delegated in memory graph. */
    private final org.jgrapht.Graph<Node, Edge> graph;

    /**
     * Creates graph operations that will forward any events on to the specified listener.
     */
    private final JGraphtNodeOperations nodeOps;

    private final JGraphtEdgeOperations edgeOps;
    private final JGraphtComponentOperations componentOps;

    private final JGraphtPathOperations pathOps;

    /**
     * Creates a new graph with a listener attached.
     */
    public JGraphtGraphOperations(final GraphListener<Node, Edge> listener) {

        final org.jgrapht.Graph<Node, Edge> base = new DirectedMultigraph<>(null, null, false);
        final ListenableGraph<Node, Edge> wrapper = new DefaultListenableGraph<>(base);
        wrapper.addGraphListener(listener);
        graph = wrapper;

        // Initialize operations
        edgeOps = new JGraphtEdgeOperations(graph);
        nodeOps = new JGraphtNodeOperations(graph);
        componentOps = new JGraphtComponentOperations(graph);
        pathOps = new JGraphtPathOperations(graph);
    }

    /**
     * Creates a graph from a pre-built JGraphT graph and components.
     * This constructor is used by the builder pattern.
     */
    JGraphtGraphOperations(final org.jgrapht.Graph<Node, Edge> builtGraph, final List<Component> components) {
        this.graph = builtGraph;

        // Initialize operations
        edgeOps = new JGraphtEdgeOperations(graph);
        nodeOps = new JGraphtNodeOperations(graph);
        componentOps = new JGraphtComponentOperations(graph);
        pathOps = new JGraphtPathOperations(graph);

        // Add components to the component operations
        for (Component component : components) {
            componentOps.addPrebuiltComponent(component);
        }
    }

    @Override
    public NodeOperations nodes() {
        return nodeOps;
    }

    @Override
    public EdgeOperations edges() {
        return edgeOps;
    }

    @Override
    public ComponentOperations components() {
        return componentOps;
    }

    @Override
    public PathOperations paths() {
        return pathOps;
    }

    @Override
    public GraphView asView() {
        return new JGraphtGraphView(graph);
    }

    @Override
    public GraphView asView(final Set<Node> nodes, final Set<Edge> edges) {
        return new JGraphtGraphView(graph, nodes, edges);
    }

    @Override
    public GraphBuilder toBuilder() {
        return new JGraphtGraphBuilder(this);
    }

    @Override
    public void close() throws Exception {
        // No resources to close for in-memory graph
    }
}
