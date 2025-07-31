/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.jgrapht.EdgeOperations;
import dev.iq.graph.model.jgrapht.GraphOperations;
import dev.iq.graph.model.jgrapht.NodeOperations;
import java.time.Instant;
import org.jgrapht.event.GraphEdgeChangeEvent;
import org.jgrapht.event.GraphListener;
import org.jgrapht.event.GraphVertexChangeEvent;
import org.jgrapht.graph.DefaultListenableGraph;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

/**
 * Tests for GraphOperations class to verify shortestPath returns Path and allPaths returns List of Path.
 */
@DisplayName("GraphOperations Tests")
public class GraphOperationsTest {

    private GraphOperations graphOps;
    private Node nodeA;
    private Node nodeB;
    private Node nodeC;

    @BeforeEach
    final void before() {

        // Create shared graph and operations
        final var base = new DirectedMultigraph<Node, Edge>(null, null, false);
        final var graph = new DefaultListenableGraph<>(base);

        // Create operations that share the same graph
        final var edgeOps = new EdgeOperations(graph);
        final var nodeOps = new NodeOperations(graph, edgeOps);

        // Create GraphOperations with an empty listener
        graphOps = new GraphOperations(new GraphListener<>() {
            @Override
            public void vertexAdded(final GraphVertexChangeEvent<Node> e) {}

            @Override
            public void vertexRemoved(final GraphVertexChangeEvent<Node> e) {}

            @Override
            public void edgeAdded(final GraphEdgeChangeEvent<Node, Edge> e) {}

            @Override
            public void edgeRemoved(final GraphEdgeChangeEvent<Node, Edge> e) {}
        });

        // Create test nodes using NodeOperations so they get added to a graph
        final var timestamp = Instant.now();
        nodeA = nodeOps.add(new TestData("NodeA"), timestamp);
        nodeB = nodeOps.add(new TestData("NodeB"), timestamp);
        nodeC = nodeOps.add(new TestData("NodeC"), timestamp);

        // Create test edges using EdgeOperations so they get added to the graph
        final var edgeAB = edgeOps.add(nodeA, nodeB, new TestData("EdgeAB"), timestamp);
        final var edgeBC = edgeOps.add(nodeB, nodeC, new TestData("EdgeBC"), timestamp);

        // Note: GraphOperations creates its own separate graph, so these nodes/edges
        // won't be in that graph. For meaningful path tests, we'd need to modify
        // GraphOperations to accept an existing graph.
    }

    // Path-related tests have been moved to PathOperationsTest
    // since shortestPath and allPaths methods were moved to PathOperations class

    /**
     * Simple Data implementation for testing.
     */
    private record TestData(Object value) implements Data {

        @Override
        public Class<?> javaClass() {
            return value.getClass();
        }
    }
}
