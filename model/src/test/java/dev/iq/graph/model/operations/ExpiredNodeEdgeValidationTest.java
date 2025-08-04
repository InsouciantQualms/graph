/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
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
 * Tests to validate that expired nodes have all connected edges expired.
 * This complements NodeOperationsReferentialIntegrityTest with additional edge cases.
 */
@DisplayName("Expired Node Edge Validation Tests")
public class ExpiredNodeEdgeValidationTest {

    private org.jgrapht.Graph<Node, Edge> graph;
    private JGraphtMutableNodeOperations nodeOps;
    private JGraphtMutableEdgeOperations edgeOps;
    private final Type defaultType = new SimpleType("test");

    @BeforeEach
    final void before() {
        graph = new DirectedMultigraph<>(null, null, false);
        edgeOps = new JGraphtMutableEdgeOperations(graph);
        nodeOps = new JGraphtMutableNodeOperations(graph, edgeOps);
    }

    @Test
    @DisplayName("Multiple incoming edges expired when node is expired")
    final void testMultipleIncomingEdgesExpiredWithNode() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes A, B, C all connecting to central node D
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp1);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp1);
        final var nodeC = nodeOps.add(defaultType, new TestData("NodeC"), new HashSet<>(), timestamp1);
        final var nodeD = nodeOps.add(defaultType, new TestData("NodeD"), new HashSet<>(), timestamp1);

        // Create edges: A->D, B->D, C->D
        edgeOps.add(defaultType, nodeA, nodeD, new TestData("EdgeAD"), new HashSet<>(), timestamp2);
        edgeOps.add(defaultType, nodeB, nodeD, new TestData("EdgeBD"), new HashSet<>(), timestamp2);
        edgeOps.add(defaultType, nodeC, nodeD, new TestData("EdgeCD"), new HashSet<>(), timestamp2);

        // Expire node D
        final var expiredNodeD = nodeOps.expire(nodeD.locator().id(), timestamp3);

        // Verify node D is expired
        assertTrue(expiredNodeD.expired().isPresent());
        assertEquals(timestamp3, expiredNodeD.expired().get());

        // Verify all edges in graph that connect to D are expired
        final var allEdges = graph.edgeSet();
        final var edgesToD = allEdges.stream()
                .filter(e -> e.target().locator().id().equals(nodeD.locator().id()))
                .toList();

        assertEquals(3, edgesToD.size(), "Should have 3 edges to node D");
        edgesToD.forEach(edge -> {
            assertTrue(edge.expired().isPresent(), "Edge to D should be expired");
            assertEquals(timestamp3, edge.expired().get(), "Edge should expire at same time as node");
        });

        // Verify source nodes remain active
        assertTrue(graph.vertexSet().stream()
                .anyMatch(n -> n.locator().id().equals(nodeA.locator().id())
                        && n.expired().isEmpty()));
        assertTrue(graph.vertexSet().stream()
                .anyMatch(n -> n.locator().id().equals(nodeB.locator().id())
                        && n.expired().isEmpty()));
        assertTrue(graph.vertexSet().stream()
                .anyMatch(n -> n.locator().id().equals(nodeC.locator().id())
                        && n.expired().isEmpty()));
    }

    @Test
    @DisplayName("Multiple outgoing edges expired when node is expired")
    final void testMultipleOutgoingEdgesExpiredWithNode() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create central node A connecting to nodes B, C, D
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp1);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp1);
        final var nodeC = nodeOps.add(defaultType, new TestData("NodeC"), new HashSet<>(), timestamp1);
        final var nodeD = nodeOps.add(defaultType, new TestData("NodeD"), new HashSet<>(), timestamp1);

        // Create edges: A->B, A->C, A->D
        edgeOps.add(defaultType, nodeA, nodeB, new TestData("EdgeAB"), new HashSet<>(), timestamp2);
        edgeOps.add(defaultType, nodeA, nodeC, new TestData("EdgeAC"), new HashSet<>(), timestamp2);
        edgeOps.add(defaultType, nodeA, nodeD, new TestData("EdgeAD"), new HashSet<>(), timestamp2);

        // Expire node A
        final var expiredNodeA = nodeOps.expire(nodeA.locator().id(), timestamp3);

        // Verify node A is expired
        assertTrue(expiredNodeA.expired().isPresent());
        assertEquals(timestamp3, expiredNodeA.expired().get());

        // Verify all edges from A are expired
        final var allEdges = graph.edgeSet();
        final var edgesFromA = allEdges.stream()
                .filter(e -> e.source().locator().id().equals(nodeA.locator().id()))
                .toList();

        assertEquals(3, edgesFromA.size(), "Should have 3 edges from node A");
        edgesFromA.forEach(edge -> {
            assertTrue(edge.expired().isPresent(), "Edge from A should be expired");
            assertEquals(timestamp3, edge.expired().get(), "Edge should expire at same time as node");
        });

        // Verify target nodes remain active
        assertTrue(graph.vertexSet().stream()
                .anyMatch(n -> n.locator().id().equals(nodeB.locator().id())
                        && n.expired().isEmpty()));
        assertTrue(graph.vertexSet().stream()
                .anyMatch(n -> n.locator().id().equals(nodeC.locator().id())
                        && n.expired().isEmpty()));
        assertTrue(graph.vertexSet().stream()
                .anyMatch(n -> n.locator().id().equals(nodeD.locator().id())
                        && n.expired().isEmpty()));
    }

    @Test
    @DisplayName("Mixed active and already-expired edges when node expires")
    final void testMixedActiveAndExpiredEdges() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);
        final var timestamp4 = timestamp3.plusSeconds(1);

        // Create nodes
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp1);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp1);
        final var nodeC = nodeOps.add(defaultType, new TestData("NodeC"), new HashSet<>(), timestamp1);

        // Create edges
        final var edgeAB = edgeOps.add(defaultType, nodeA, nodeB, new TestData("EdgeAB"), new HashSet<>(), timestamp2);
        final var edgeBC = edgeOps.add(defaultType, nodeB, nodeC, new TestData("EdgeBC"), new HashSet<>(), timestamp2);

        // Manually expire one edge before expiring the node
        edgeOps.expire(edgeAB.locator().id(), timestamp3);

        // Expire node B
        nodeOps.expire(nodeB.locator().id(), timestamp4);

        // Verify both edges are expired but with different timestamps
        final var allEdges = graph.edgeSet();

        final var edgeAbInGraph = allEdges.stream()
                .filter(e -> e.locator().id().equals(edgeAB.locator().id()))
                .findFirst()
                .orElseThrow();
        final var edgeBcInGraph = allEdges.stream()
                .filter(e -> e.locator().id().equals(edgeBC.locator().id()))
                .findFirst()
                .orElseThrow();

        // Edge AB was expired earlier
        assertTrue(edgeAbInGraph.expired().isPresent());
        assertEquals(timestamp3, edgeAbInGraph.expired().get(), "Edge AB should keep its original expiry time");

        // Edge BC was expired with the node
        assertTrue(edgeBcInGraph.expired().isPresent());
        assertEquals(timestamp4, edgeBcInGraph.expired().get(), "Edge BC should expire with the node");
    }

    @Test
    @DisplayName("Bidirectional edges expired when node expires")
    final void testBidirectionalEdgesExpired() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create two nodes with edges in both directions
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp1);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp1);
        final var edgeAB = edgeOps.add(defaultType, nodeA, nodeB, new TestData("EdgeAB"), new HashSet<>(), timestamp2);
        final var edgeBA = edgeOps.add(defaultType, nodeB, nodeA, new TestData("EdgeBA"), new HashSet<>(), timestamp2);

        // Expire node A
        nodeOps.expire(nodeA.locator().id(), timestamp3);

        // Verify both edges are expired
        final var edgeAbInGraph = graph.edgeSet().stream()
                .filter(e -> e.locator().id().equals(edgeAB.locator().id()))
                .findFirst()
                .orElseThrow();
        final var edgeBaInGraph = graph.edgeSet().stream()
                .filter(e -> e.locator().id().equals(edgeBA.locator().id()))
                .findFirst()
                .orElseThrow();

        assertTrue(edgeAbInGraph.expired().isPresent());
        assertEquals(timestamp3, edgeAbInGraph.expired().get());
        assertTrue(edgeBaInGraph.expired().isPresent());
        assertEquals(timestamp3, edgeBaInGraph.expired().get());
    }

    @Test
    @DisplayName("No active edges remain after node expiry in complex graph")
    final void testNoActiveEdgesRemainInComplexGraph() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create complex graph with node B as hub
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp1);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp1);
        final var nodeC = nodeOps.add(defaultType, new TestData("NodeC"), new HashSet<>(), timestamp1);
        final var nodeD = nodeOps.add(defaultType, new TestData("NodeD"), new HashSet<>(), timestamp1);
        final var nodeE = nodeOps.add(defaultType, new TestData("NodeE"), new HashSet<>(), timestamp1);

        // Create edges connecting to B from multiple directions
        edgeOps.add(defaultType, nodeA, nodeB, new TestData("EdgeAB"), new HashSet<>(), timestamp2);
        edgeOps.add(defaultType, nodeB, nodeC, new TestData("EdgeBC"), new HashSet<>(), timestamp2);
        edgeOps.add(defaultType, nodeD, nodeB, new TestData("EdgeDB"), new HashSet<>(), timestamp2);
        edgeOps.add(defaultType, nodeB, nodeE, new TestData("EdgeBE"), new HashSet<>(), timestamp2);

        // Expire node B
        nodeOps.expire(nodeB.locator().id(), timestamp3);

        // Count active edges connected to B
        final var activeEdgesConnectedToB = graph.edgeSet().stream()
                .filter(e -> e.expired().isEmpty())
                .filter(e -> e.source().locator().id().equals(nodeB.locator().id())
                        || e.target().locator().id().equals(nodeB.locator().id()))
                .count();

        assertEquals(0, activeEdgesConnectedToB, "No active edges should be connected to expired node B");

        // Verify we can still add edges between other nodes
        final var newEdge = edgeOps.add(defaultType, nodeA, nodeC, new TestData("EdgeAC"), new HashSet<>(), timestamp3);
        assertFalse(newEdge.expired().isPresent(), "New edge between active nodes should be active");
    }

    /**
     * Simple Data implementation for testing.
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
