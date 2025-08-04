/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.jgrapht.JGraphtPathOperations;
import dev.iq.graph.model.jgrapht.mutable.JGraphtMutableEdgeOperations;
import dev.iq.graph.model.jgrapht.mutable.JGraphtMutableNodeOperations;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import java.util.HashSet;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for PathOperations interface.
 */
@DisplayName("Path Operations Tests")
public class PathOperationsTest {

    private org.jgrapht.Graph<Node, Edge> graph;
    private JGraphtMutableNodeOperations nodeOps;
    private JGraphtMutableEdgeOperations edgeOps;
    private PathOperations pathOps;
    private final Type defaultType = new SimpleType("test");

    @BeforeEach
    final void before() {
        graph = new DirectedMultigraph<>(null, null, false);
        edgeOps = new JGraphtMutableEdgeOperations(graph);
        nodeOps = new JGraphtMutableNodeOperations(graph, edgeOps);
        pathOps = new JGraphtPathOperations(graph);
    }

    @Test
    @DisplayName("Find shortest path between connected nodes")
    final void testShortestPath() {
        final var timestamp = Instant.now();

        // Create nodes
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp);
        final var nodeC = nodeOps.add(defaultType, new TestData("NodeC"), new HashSet<>(), timestamp);

        // Create edges A -> B -> C
        edgeOps.add(defaultType, nodeA, nodeB, new TestData("EdgeAB"), new HashSet<>(), timestamp);
        edgeOps.add(defaultType, nodeB, nodeC, new TestData("EdgeBC"), new HashSet<>(), timestamp);

        // Find shortest path
        final var pathOpt = pathOps.findShortestPath(nodeA, nodeC);

        assertTrue(pathOpt.isPresent());
        final var path = pathOpt.get();
        // Path contains alternating nodes and edges: nodeA -> edgeAB -> nodeB -> edgeBC -> nodeC
        assertEquals(5, path.elements().size());
        assertEquals(nodeA, path.elements().get(0));
        assertEquals(nodeC, path.elements().get(4));
    }

    @Test
    @DisplayName("Path exists returns true for connected nodes")
    final void testPathExists() {
        final var timestamp = Instant.now();

        // Create nodes
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp);

        // Initially no path
        assertFalse(pathOps.pathExists(nodeA, nodeB));

        // Add edge
        edgeOps.add(defaultType, nodeA, nodeB, new TestData("EdgeAB"), new HashSet<>(), timestamp);

        // Now path exists
        assertTrue(pathOps.pathExists(nodeA, nodeB));
    }

    @Test
    @DisplayName("Find all paths between nodes")
    final void testAllPaths() {
        final var timestamp = Instant.now();

        // Create diamond pattern: A -> B -> D and A -> C -> D
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp);
        final var nodeC = nodeOps.add(defaultType, new TestData("NodeC"), new HashSet<>(), timestamp);
        final var nodeD = nodeOps.add(defaultType, new TestData("NodeD"), new HashSet<>(), timestamp);

        // Create edges
        edgeOps.add(defaultType, nodeA, nodeB, new TestData("EdgeAB"), new HashSet<>(), timestamp);
        edgeOps.add(defaultType, nodeB, nodeD, new TestData("EdgeBD"), new HashSet<>(), timestamp);
        edgeOps.add(defaultType, nodeA, nodeC, new TestData("EdgeAC"), new HashSet<>(), timestamp);
        edgeOps.add(defaultType, nodeC, nodeD, new TestData("EdgeCD"), new HashSet<>(), timestamp);

        // Find all paths
        final var paths = pathOps.findAllPaths(nodeA, nodeD);

        assertNotNull(paths);
        assertEquals(2, paths.size(), "Should find 2 paths from A to D");
    }

    /**
     * Test data implementation.
     */
    private record TestData(String value) implements Data {
        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<?> javaClass() {
            return String.class;
        }
    }
}
