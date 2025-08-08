/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.graph.model.Edge;
import dev.iq.graph.model.GraphSpace;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.jgrapht.mutable.JGraphtMutableComponentOperations;
import dev.iq.graph.model.jgrapht.mutable.JGraphtMutableEdgeOperations;
import dev.iq.graph.model.jgrapht.mutable.JGraphtMutableNodeOperations;
import dev.iq.graph.model.operations.ComponentOperations;
import dev.iq.graph.model.operations.EdgeOperations;
import dev.iq.graph.model.operations.mutable.NodeOperations;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.graph.builder.GraphBuilder;

/**
 * Builder implementation for constructing JGraphT-based graphs.
 * This builder accumulates nodes, edges, and components without any listeners attached,
 * making it ideal for loading from persistence or bulk construction.
 */
public final class JGraphtGraphBuilder implements GraphBuilder {

    private final Graph<Node, Edge> jgraphtGraph;
    private final JGraphtMutableNodeOperations nodeOperations;
    private final JGraphtMutableEdgeOperations edgeOperations;
    private final JGraphtMutableComponentOperations componentOperations;

    public JGraphtGraphBuilder() {

        jgraphtGraph = new DirectedMultigraph<>(null, null, false);
        edgeOperations = new JGraphtMutableEdgeOperations(jgraphtGraph);
        nodeOperations = new JGraphtMutableNodeOperations(jgraphtGraph, edgeOperations);
        componentOperations = new JGraphtMutableComponentOperations(jgraphtGraph, nodeOperations, edgeOperations);
    }

    @Override
    public NodeOperations nodes() {
        return nodeOperations;
    }

    @Override
    public EdgeOperations edges() {
        return edgeOperations;
    }

    @Override
    public ComponentOperations components() {
        return componentOperations;
    }

    @Override
    public GraphSpace build() {
        // Create a new JGraphtGraphOperations without a listener
        // The caller can add a listener after construction if needed
        return new JGraphtGraphOperations(jgraphtGraph, componentOperations.allActive());
    }
}
