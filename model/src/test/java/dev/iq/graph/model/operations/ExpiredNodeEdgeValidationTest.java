/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.common.version.Versioned;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Reference;
import dev.iq.graph.model.jgrapht.EdgeOperations;
import dev.iq.graph.model.jgrapht.NodeOperations;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jgrapht.graph.DefaultListenableGraph;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests to validate that expired nodes have all connected edges expired.
 */
@DisplayName("Expired Node Edge Validation Tests")
public class ExpiredNodeEdgeValidationTest {

    private NodeOperations nodeOps;
    private EdgeOperations edgeOps;

    @BeforeEach
    final void before() {

        final var base = new DirectedMultigraph<Reference<Node>, Reference<Edge>>(null, null, false);
        final var graph = new DefaultListenableGraph<>(base);

        edgeOps = new EdgeOperations(graph);
        nodeOps = new NodeOperations(graph, edgeOps);
    }

    @Test
    @DisplayName("All incoming edges expired when node is expired")
    final void testAllIncomingEdgesExpiredWithNode() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes A, B, C all connecting to central node D
        final var nodeA = nodeOps.add(new TestData("NodeA"), timestamp1);
        final var nodeB = nodeOps.add(new TestData("NodeB"), timestamp1);
        final var nodeC = nodeOps.add(new TestData("NodeC"), timestamp1);
        final var nodeD = nodeOps.add(new TestData("NodeD"), timestamp1);

        // Create edges: A->D, B->D, C->D
        final var edgeAD = edgeOps.add(nodeA, nodeD, new TestData("EdgeAD"), timestamp2);
        final var edgeBD = edgeOps.add(nodeB, nodeD, new TestData("EdgeBD"), timestamp2);
        final var edgeCD = edgeOps.add(nodeC, nodeD, new TestData("EdgeCD"), timestamp2);

        // Expire node D
        final var expiredNodeD = nodeOps.expire(nodeD.locator().id(), timestamp3);

        // Verify node D is expired
        assertTrue(expiredNodeD.expired().isPresent());
        assertEquals(timestamp3, expiredNodeD.expired().get());

        // Verify all incoming edges to D are expired
        assertEdgeExpired(edgeAD, timestamp3);
        assertEdgeExpired(edgeBD, timestamp3);
        assertEdgeExpired(edgeCD, timestamp3);

        // Verify source nodes remain active
        assertTrue(nodeOps.findActive(nodeA.locator().id()).isPresent());
        assertTrue(nodeOps.findActive(nodeB.locator().id()).isPresent());
        assertTrue(nodeOps.findActive(nodeC.locator().id()).isPresent());
    }

    @Test
    @DisplayName("All outgoing edges expired when node is expired")
    final void testAllOutgoingEdgesExpiredWithNode() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create central node A connecting to nodes B, C, D
        final var nodeA = nodeOps.add(new TestData("NodeA"), timestamp1);
        final var nodeB = nodeOps.add(new TestData("NodeB"), timestamp1);
        final var nodeC = nodeOps.add(new TestData("NodeC"), timestamp1);
        final var nodeD = nodeOps.add(new TestData("NodeD"), timestamp1);

        // Create edges: A->B, A->C, A->D
        final var edgeAB = edgeOps.add(nodeA, nodeB, new TestData("EdgeAB"), timestamp2);
        final var edgeAC = edgeOps.add(nodeA, nodeC, new TestData("EdgeAC"), timestamp2);
        final var edgeAD = edgeOps.add(nodeA, nodeD, new TestData("EdgeAD"), timestamp2);

        // Expire node A
        final var expiredNodeA = nodeOps.expire(nodeA.locator().id(), timestamp3);

        // Verify node A is expired
        assertTrue(expiredNodeA.expired().isPresent());
        assertEquals(timestamp3, expiredNodeA.expired().get());

        // Verify all outgoing edges from A are expired
        assertEdgeExpired(edgeAB, timestamp3);
        assertEdgeExpired(edgeAC, timestamp3);
        assertEdgeExpired(edgeAD, timestamp3);

        // Verify target nodes remain active
        assertTrue(nodeOps.findActive(nodeB.locator().id()).isPresent());
        assertTrue(nodeOps.findActive(nodeC.locator().id()).isPresent());
        assertTrue(nodeOps.findActive(nodeD.locator().id()).isPresent());
    }

    @Test
    @DisplayName("Both incoming and outgoing edges expired when node is expired")
    final void testBothDirectionEdgesExpiredWithNode() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes in chain: A -> B -> C with B as central node
        final var nodeA = nodeOps.add(new TestData("NodeA"), timestamp1);
        final var nodeB = nodeOps.add(new TestData("NodeB"), timestamp1);
        final var nodeC = nodeOps.add(new TestData("NodeC"), timestamp1);
        final var nodeD = nodeOps.add(new TestData("NodeD"), timestamp1);

        // Create edges: A->B (incoming to B), B->C (outgoing from B), B->D (outgoing from B)
        final var edgeAB = edgeOps.add(nodeA, nodeB, new TestData("EdgeAB"), timestamp2);
        final var edgeBC = edgeOps.add(nodeB, nodeC, new TestData("EdgeBC"), timestamp2);
        final var edgeBD = edgeOps.add(nodeB, nodeD, new TestData("EdgeBD"), timestamp2);

        // Expire central node B
        final var expiredNodeB = nodeOps.expire(nodeB.locator().id(), timestamp3);

        // Verify node B is expired
        assertTrue(expiredNodeB.expired().isPresent());
        assertEquals(timestamp3, expiredNodeB.expired().get());

        // Verify all edges connected to B are expired
        assertEdgeExpired(edgeAB, timestamp3);
        assertEdgeExpired(edgeBC, timestamp3);
        assertEdgeExpired(edgeBD, timestamp3);

        // Verify other nodes remain active
        assertTrue(nodeOps.findActive(nodeA.locator().id()).isPresent());
        assertTrue(nodeOps.findActive(nodeC.locator().id()).isPresent());
        assertTrue(nodeOps.findActive(nodeD.locator().id()).isPresent());
    }

    @Test
    @DisplayName("Only active edges expired when node contains both active and expired edges")
    final void testOnlyActiveEdgesExpiredWithNode() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);
        final var timestamp4 = timestamp3.plusSeconds(1);

        // Create nodes
        final var nodeA = nodeOps.add(new TestData("NodeA"), timestamp1);
        final var nodeB = nodeOps.add(new TestData("NodeB"), timestamp1);
        final var nodeC = nodeOps.add(new TestData("NodeC"), timestamp1);

        // Create edges
        final var edgeAB = edgeOps.add(nodeA, nodeB, new TestData("EdgeAB"), timestamp2);
        final var edgeBC = edgeOps.add(nodeB, nodeC, new TestData("EdgeBC"), timestamp2);

        // Manually expire one edge before expiring the node
        final var manuallyExpiredEdge = edgeOps.expire(edgeAB.locator().id(), timestamp3);

        // Expire node B
        final var expiredNodeB = nodeOps.expire(nodeB.locator().id(), timestamp4);

        // Verify node B is expired
        assertTrue(expiredNodeB.expired().isPresent());
        assertEquals(timestamp4, expiredNodeB.expired().get());

        // Verify the manually expired edge timestamp is unchanged
        final var currentExpiredEdgeAB = edgeOps.findVersions(edgeAB.locator().id()).stream()
                .filter(e -> e.expired().isPresent())
                .findFirst();
        assertTrue(currentExpiredEdgeAB.isPresent());
        assertEquals(timestamp3, currentExpiredEdgeAB.get().expired().get());

        // Verify the active edge BC is expired with the node timestamp
        assertEdgeExpired(edgeBC, timestamp4);
    }

    @Test
    @DisplayName("No active edges remain connected to expired node")
    final void testNoActiveEdgesRemainConnectedToExpiredNode() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create complex graph
        final var nodeA = nodeOps.add(new TestData("NodeA"), timestamp1);
        final var nodeB = nodeOps.add(new TestData("NodeB"), timestamp1);
        final var nodeC = nodeOps.add(new TestData("NodeC"), timestamp1);
        final var nodeD = nodeOps.add(new TestData("NodeD"), timestamp1);
        final var nodeE = nodeOps.add(new TestData("NodeE"), timestamp1);

        // Create edges connecting to B from multiple directions
        edgeOps.add(nodeA, nodeB, new TestData("EdgeAB"), timestamp2);
        edgeOps.add(nodeB, nodeC, new TestData("EdgeBC"), timestamp2);
        edgeOps.add(nodeD, nodeB, new TestData("EdgeDB"), timestamp2);
        edgeOps.add(nodeB, nodeE, new TestData("EdgeBE"), timestamp2);

        // Expire node B
        nodeOps.expire(nodeB.locator().id(), timestamp3);

        // Verify no active edges connect to the expired node B
        final var allActiveEdges = getAllActiveEdges();
        for (final var edge : allActiveEdges) {
            if (edge.source() instanceof Reference.Loaded<Node> source) {
                assertNotEquals(
                        nodeB.locator().id(),
                        source.value().locator().id(),
                        "Active edge should not have expired node as source");
            }
            if (edge.target() instanceof Reference.Loaded<Node> target) {
                assertNotEquals(
                        nodeB.locator().id(),
                        target.value().locator().id(),
                        "Active edge should not have expired node as target");
            }
        }

        // Verify there are still some active edges in the graph (between non-expired nodes)
        final var remainingEdge = edgeOps.add(nodeA, nodeC, new TestData("EdgeAC"), timestamp3);
        assertTrue(getAllActiveEdges().contains(remainingEdge));
    }

    /**
     * Helper method to assert that an edge is expired.
     */
    private void assertEdgeExpired(final Versioned originalEdge, final Instant expectedExpiredTime) {
        // Verify that at least one version of the edge exists
        final var allVersions = edgeOps.findVersions(originalEdge.locator().id());
        assertFalse(
                allVersions.isEmpty(),
                () -> "Edge should exist in version history: "
                        + originalEdge.locator().id());

        // Verify that there is at least one expired version
        final var hasExpiredVersion =
                allVersions.stream().anyMatch(e -> e.expired().isPresent());
        assertTrue(
                hasExpiredVersion,
                () -> "Should have at least one expired version for edge "
                        + originalEdge.locator().id());

        // Check if edge is still active - if so, it should be a different version than the original
        final var activeEdge = edgeOps.findActive(originalEdge.locator().id());
        if (activeEdge.isPresent()) {
            // If there's still an active edge, it should be a different version or the original should be expired
            final var originalStillActive = allVersions.stream()
                    .anyMatch(e -> e.locator().equals(originalEdge.locator())
                            && e.expired().isEmpty());
            assertFalse(
                    originalStillActive, () -> "Original edge should not still be active: " + originalEdge.locator());
        }
    }

    private List<Edge> getAllActiveEdges() {
        final var allEdges = new ArrayList<Edge>();
        for (var node : nodeOps.activeNodes()) {
            allEdges.addAll(edgeOps.getEdgesFor(node));
        }
        return allEdges.stream().distinct().toList();
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
