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
import java.util.Set;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for PathOperations class to verify shortestPath returns Path and allPaths returns List of Path.
 */
@DisplayName("PathOperations Tests")
public class PathOperationsTest {

    private JGraphtPathOperations pathOps;
    private Node nodeA;
    private Node nodeB;
    private Node nodeC;

    @BeforeEach
    final void before() {

        // Create shared graph and operations
        final var graph = new DirectedMultigraph<Node, Edge>(null, null, false);

        // Create PathOperations with the graph
        pathOps = new JGraphtPathOperations(graph);

        // Create test nodes
        final var timestamp = Instant.now();
        final var nodeType = new SimpleType("NODE");
        final Set<Locator> emptyComponents = new HashSet<>();

        final var locatorA = new Locator(new NanoId("nodeA"), 1);
        nodeA = new SimpleNode(locatorA, nodeType, new TestData("NodeA"), emptyComponents, timestamp, Optional.empty());
        final var locatorB = new Locator(new NanoId("nodeB"), 1);
        nodeB = new SimpleNode(locatorB, nodeType, new TestData("NodeB"), emptyComponents, timestamp, Optional.empty());
        final var locatorC = new Locator(new NanoId("nodeC"), 1);
        nodeC = new SimpleNode(locatorC, nodeType, new TestData("NodeC"), emptyComponents, timestamp, Optional.empty());

        // Add nodes to graph
        graph.addVertex(nodeA);
        graph.addVertex(nodeB);
        graph.addVertex(nodeC);

        // Create test edges
        final var edgeLocatorAB = new Locator(new NanoId("edgeAB"), 1);
        final var edgeLocatorBC = new Locator(new NanoId("edgeBC"), 1);
        final var edgeType = new SimpleType("EDGE");
        final var edgeAB = new SimpleEdge(
                edgeLocatorAB,
                edgeType,
                nodeA,
                nodeB,
                new TestData("EdgeAB"),
                emptyComponents,
                timestamp,
                Optional.empty());
        final var edgeBC = new SimpleEdge(
                edgeLocatorBC,
                edgeType,
                nodeB,
                nodeC,
                new TestData("EdgeBC"),
                emptyComponents,
                timestamp,
                Optional.empty());

        // Add edges to graph
        graph.addEdge(nodeA, nodeB, edgeAB);
        graph.addEdge(nodeB, nodeC, edgeBC);
    }

    @Test
    @DisplayName("shortestPath returns Path instance")
    final void testShortestPathReturnsPath() {
        // Test that findShortestPath method returns an Optional<Path>
        final var pathOpt = pathOps.findShortestPath(nodeA, nodeB);
        assertNotNull(pathOpt);
        assertTrue(pathOpt.isPresent());
        assertInstanceOf(Path.class, pathOpt.get());
    }

    @Test
    @DisplayName("allPaths returns List<Path>")
    final void testAllPathsReturnsList() {
        // Test that findAllPaths method returns a List<Path>
        final var paths = pathOps.findAllPaths(nodeA, nodeC);
        assertNotNull(paths);
        assertTrue(paths instanceof List<?>);
        // Verify that if list is not empty, it contains Path instances
        if (!paths.isEmpty()) {
            assertInstanceOf(Path.class, paths.getFirst());
        }
    }

    @Test
    @DisplayName("pathExists returns boolean")
    final void testPathExistsReturnsBoolean() {
        // Test that pathExists method returns a boolean
        final var exists = pathOps.pathExists(nodeA, nodeC);
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
