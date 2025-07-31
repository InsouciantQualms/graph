/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Path;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for PathOperations class to verify shortestPath returns Path and allPaths returns List of Path.
 */
@DisplayName("PathOperations Tests")
public class PathOperationsTest {

    private PathOperations pathOps;
    private Node nodeA;
    private Node nodeB;
    private Node nodeC;

    @BeforeEach
    final void before() {

        // Create shared graph and operations
        final var graph = new DirectedMultigraph<Node, Edge>(null, null, false);

        // Create PathOperations with the graph
        pathOps = new PathOperations(graph);

        // Create test nodes
        final var timestamp = Instant.now();
        final var nodeType = new SimpleType("NODE");
        final var edgeType = new SimpleType("EDGE");

        final var locatorA = new Locator(new NanoId("nodeA"), 1);
        final var locatorB = new Locator(new NanoId("nodeB"), 1);
        final var locatorC = new Locator(new NanoId("nodeC"), 1);

        nodeA = new SimpleNode(
                locatorA, nodeType, List.of(), new TestData("NodeA"), timestamp, Optional.empty(), new HashSet<>());
        nodeB = new SimpleNode(
                locatorB, nodeType, List.of(), new TestData("NodeB"), timestamp, Optional.empty(), new HashSet<>());
        nodeC = new SimpleNode(
                locatorC, nodeType, List.of(), new TestData("NodeC"), timestamp, Optional.empty(), new HashSet<>());

        // Add nodes to graph
        graph.addVertex(nodeA);
        graph.addVertex(nodeB);
        graph.addVertex(nodeC);

        // Create test edges
        final var edgeLocatorAB = new Locator(new NanoId("edgeAB"), 1);
        final var edgeLocatorBC = new Locator(new NanoId("edgeBC"), 1);
        final var edgeAB = new SimpleEdge(
                edgeLocatorAB,
                edgeType,
                nodeA,
                nodeB,
                new TestData("EdgeAB"),
                timestamp,
                Optional.empty(),
                new HashSet<>());
        final var edgeBC = new SimpleEdge(
                edgeLocatorBC,
                edgeType,
                nodeB,
                nodeC,
                new TestData("EdgeBC"),
                timestamp,
                Optional.empty(),
                new HashSet<>());

        // Add edges to graph
        graph.addEdge(nodeA, nodeB, edgeAB);
        graph.addEdge(nodeB, nodeC, edgeBC);
    }

    @Test
    @DisplayName("shortestPath returns Path instance")
    final void testShortestPathReturnsPath() {
        // Test that shortestPath method returns a Path
        final var path = pathOps.shortestPath(nodeA, nodeB);
        assertNotNull(path);
        assertInstanceOf(Path.class, path);
    }

    @Test
    @DisplayName("allPaths returns List<Path>")
    final void testAllPathsReturnsList() {
        // Test that allPaths method returns a List<Path>
        final var paths = pathOps.allPaths(nodeA, nodeC);
        assertNotNull(paths);
        assertInstanceOf(List.class, paths);
        // Verify that if list is not empty, it contains Path instances
        if (!paths.isEmpty()) {
            assertInstanceOf(Path.class, paths.get(0));
        }
    }

    @Test
    @DisplayName("pathExists returns boolean")
    final void testPathExistsReturnsBoolean() {
        // Test that pathExists method returns a boolean
        final var exists = pathOps.pathExists(nodeA, nodeC);
        assertInstanceOf(Boolean.class, exists);
        // Should be true since there's a path A->B->C
        assertTrue(exists);
    }

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
