/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.common.version.Locator;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.jgrapht.JGraphtEdgeOperations;
import dev.iq.graph.model.jgrapht.JGraphtNodeOperations;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for EdgeOperations referential integrity.
 * Tests the rules:
 * - Edge operations are isolated - no cascading effects
 * - When an edge expires, only the edge itself expires (nodes remain unaffected)
 * - When an edge updates, only the edge itself updates (nodes remain unaffected)
 */
@DisplayName("Edge Operations Referential Integrity Tests")
public class EdgeOperationsReferentialIntegrityTest {

    private Graph<Node, Edge> graph;
    private JGraphtNodeOperations nodeOps;
    private JGraphtEdgeOperations edgeOps;
    private final Type defaultType = new SimpleType("test");

    @BeforeEach
    final void before() {
        graph = new DirectedMultigraph<>(null, null, false);
        edgeOps = new JGraphtEdgeOperations(graph);
        nodeOps = new JGraphtNodeOperations(graph, edgeOps);
    }

    @Test
    @DisplayName("Edge update does not affect connected nodes")
    final void testEdgeUpdateIsolation() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create two nodes
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp1);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp1);

        // Create edge A -> B
        final var originalEdge =
                edgeOps.add(defaultType, nodeA, nodeB, new TestData("OriginalEdge"), new HashSet<>(), timestamp2);

        // Update edge data
        final var updatedEdge = edgeOps.update(originalEdge.locator().id(), new TestData("UpdatedEdge"), timestamp3);

        // Verify edge has new version
        assertEquals(2, updatedEdge.locator().version());
        assertEquals("UpdatedEdge", updatedEdge.data().value());

        // Verify nodes remain unchanged (no new versions created)
        final var currentNodeA = graph.vertexSet().stream()
                .filter(n -> n.locator().id().equals(nodeA.locator().id()))
                .findFirst()
                .orElseThrow();
        final var currentNodeB = graph.vertexSet().stream()
                .filter(n -> n.locator().id().equals(nodeB.locator().id()))
                .findFirst()
                .orElseThrow();

        assertEquals(1, currentNodeA.locator().version(), "Node A should still be version 1");
        assertEquals(1, currentNodeB.locator().version(), "Node B should still be version 1");
        assertTrue(currentNodeA.expired().isEmpty(), "Node A should not be expired");
        assertTrue(currentNodeB.expired().isEmpty(), "Node B should not be expired");

        // Verify endpoints remain the same (references to original nodes)
        assertEquals(nodeA.locator(), updatedEdge.source().locator());
        assertEquals(nodeB.locator(), updatedEdge.target().locator());
    }

    @Test
    @DisplayName("Edge expire does not affect connected nodes")
    final void testEdgeExpireIsolation() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes and edges
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp1);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp1);
        final var nodeC = nodeOps.add(defaultType, new TestData("NodeC"), new HashSet<>(), timestamp1);

        final var edgeAB = edgeOps.add(defaultType, nodeA, nodeB, new TestData("EdgeAB"), new HashSet<>(), timestamp2);
        final var edgeBC = edgeOps.add(defaultType, nodeB, nodeC, new TestData("EdgeBC"), new HashSet<>(), timestamp2);

        // Expire one edge
        final var expiredEdgeAB = edgeOps.expire(edgeAB.locator().id(), timestamp3);

        // Verify expired edge
        assertTrue(expiredEdgeAB.expired().isPresent());
        assertEquals(timestamp3, expiredEdgeAB.expired().get());

        // Verify all nodes remain active and unchanged
        final var allNodes = graph.vertexSet();
        assertEquals(3, allNodes.size(), "Should still have 3 nodes");

        allNodes.forEach(node -> {
            assertEquals(1, node.locator().version(), "All nodes should still be version 1");
            assertTrue(node.expired().isEmpty(), "No nodes should be expired");
        });

        // Verify other edge remains active
        final var edgeBcInGraph = graph.edgeSet().stream()
                .filter(e -> e.locator().id().equals(edgeBC.locator().id()))
                .findFirst()
                .orElseThrow();
        assertTrue(edgeBcInGraph.expired().isEmpty(), "Edge BC should not be expired");
    }

    @Test
    @DisplayName("Multiple edge operations maintain isolation")
    final void testMultipleEdgeOperationsIsolation() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);
        final var timestamp4 = timestamp3.plusSeconds(1);
        final var timestamp5 = timestamp4.plusSeconds(1);

        // Create nodes
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp1);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp1);

        // Create and update edge multiple times
        final var edge1 = edgeOps.add(defaultType, nodeA, nodeB, new TestData("Version1"), new HashSet<>(), timestamp2);
        final var edge2 = edgeOps.update(edge1.locator().id(), new TestData("Version2"), timestamp3);
        final var edge3 = edgeOps.update(edge1.locator().id(), new TestData("Version3"), timestamp4);

        // Verify edge versions
        assertEquals(1, edge1.locator().version());
        assertEquals(2, edge2.locator().version());
        assertEquals(3, edge3.locator().version());

        // Verify nodes remain at version 1 throughout all edge operations
        final var currentNodes = graph.vertexSet();
        currentNodes.forEach(node -> {
            assertEquals(1, node.locator().version(), "Nodes should not be versioned by edge operations");
            assertTrue(node.expired().isEmpty(), "Nodes should not be expired by edge operations");
        });

        // Now expire the edge
        final var expiredEdge = edgeOps.expire(edge1.locator().id(), timestamp5);
        assertTrue(expiredEdge.expired().isPresent());

        // Verify nodes still remain unaffected after edge expiry
        graph.vertexSet().forEach(node -> {
            assertEquals(1, node.locator().version(), "Nodes should not be versioned by edge expiry");
            assertTrue(node.expired().isEmpty(), "Nodes should not be expired by edge expiry");
        });
    }

    @Test
    @DisplayName("Edge update with type change maintains isolation")
    final void testEdgeUpdateWithTypeIsolation() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp1);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp1);

        // Create edge with one type
        final var originalType = new SimpleType("original");
        final var edge = edgeOps.add(originalType, nodeA, nodeB, new TestData("Edge"), new HashSet<>(), timestamp2);

        // Update edge with new type
        final var newType = new SimpleType("updated");
        final var updatedEdge = edgeOps.update(edge.locator().id(), newType, new TestData("UpdatedEdge"), timestamp3);

        // Verify edge was updated
        assertEquals(2, updatedEdge.locator().version());
        assertEquals("updated", updatedEdge.type().code());

        // Verify nodes remain unchanged
        graph.vertexSet().forEach(node -> {
            assertEquals(1, node.locator().version(), "Nodes should not be affected by edge type change");
            assertTrue(node.expired().isEmpty());
        });
    }

    @Test
    @DisplayName("Edge update components maintains isolation")
    final void testEdgeUpdateComponentsIsolation() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp1);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp1);

        // Create edge with initial components
        final Set<Locator> initialComponents = new HashSet<>();
        initialComponents.add(Locator.generate());

        final var edge = edgeOps.add(defaultType, nodeA, nodeB, new TestData("Edge"), initialComponents, timestamp2);

        // Update edge components
        final Set<Locator> newComponents = new HashSet<>();
        newComponents.add(Locator.generate());
        newComponents.add(Locator.generate());

        final var updatedEdge = edgeOps.updateComponents(edge.locator().id(), newComponents, timestamp3);

        // Verify edge was updated
        assertEquals(2, updatedEdge.locator().version());
        assertEquals(newComponents, updatedEdge.components());

        // Verify nodes remain unchanged
        graph.vertexSet().forEach(node -> {
            assertEquals(1, node.locator().version(), "Nodes should not be affected by edge component update");
            assertTrue(node.expired().isEmpty());
        });
    }

    @Test
    @DisplayName("Complex graph edge operations maintain isolation")
    final void testComplexGraphEdgeIsolation() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);
        final var timestamp4 = timestamp3.plusSeconds(1);

        // Create a more complex graph: A -> B -> C, A -> C, B -> D
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp1);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp1);
        final var nodeC = nodeOps.add(defaultType, new TestData("NodeC"), new HashSet<>(), timestamp1);
        final var nodeD = nodeOps.add(defaultType, new TestData("NodeD"), new HashSet<>(), timestamp1);

        final var edgeAB = edgeOps.add(defaultType, nodeA, nodeB, new TestData("EdgeAB"), new HashSet<>(), timestamp2);
        final var edgeBC = edgeOps.add(defaultType, nodeB, nodeC, new TestData("EdgeBC"), new HashSet<>(), timestamp2);
        final var edgeAC = edgeOps.add(defaultType, nodeA, nodeC, new TestData("EdgeAC"), new HashSet<>(), timestamp2);
        final var edgeBD = edgeOps.add(defaultType, nodeB, nodeD, new TestData("EdgeBD"), new HashSet<>(), timestamp2);

        // Update one edge
        edgeOps.update(edgeAB.locator().id(), new TestData("UpdatedAB"), timestamp3);

        // Expire another edge
        edgeOps.expire(edgeBC.locator().id(), timestamp4);

        // Verify all nodes remain at version 1
        graph.vertexSet().forEach(node -> {
            assertEquals(1, node.locator().version(), "No node should be versioned by edge operations");
            assertTrue(node.expired().isEmpty(), "No node should be expired by edge operations");
        });

        // Verify edge states
        final var currentEdges = graph.edgeSet();
        assertEquals(5, currentEdges.size(), "Should have 5 edges total (original + updated version)");

        // Count active edges
        final var activeEdges =
                currentEdges.stream().filter(e -> e.expired().isEmpty()).count();
        assertEquals(3, activeEdges, "Should have 3 active edges (AC, BD, and updated AB)");
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
