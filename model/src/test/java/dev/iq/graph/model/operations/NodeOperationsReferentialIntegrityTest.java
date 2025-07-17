/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.*;

import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.jgrapht.EdgeOperations;
import dev.iq.graph.model.jgrapht.NodeOperations;
import java.time.Instant;
import org.jgrapht.graph.DefaultListenableGraph;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for NodeOperations referential integrity when creating new node versions.
 */
@DisplayName("Node Operations Referential Integrity Tests")
public class NodeOperationsReferentialIntegrityTest {

    private NodeOperations nodeOps;
    private EdgeOperations edgeOps;

    @BeforeEach
    final void setUp() {

        final var base = new DirectedMultigraph<Node, Edge>(null, null, false);
        final var graph = new DefaultListenableGraph<>(base);

        edgeOps = new EdgeOperations(graph);
        nodeOps = new NodeOperations(graph, edgeOps);
    }

    @Test
    @DisplayName("Node update preserves connections to new version")
    final void testNodeUpdatePreservesConnections() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create three nodes: A, B, C
        final var nodeA = nodeOps.add(new TestData("NodeA"), timestamp1);
        final var nodeB = nodeOps.add(new TestData("NodeB"), timestamp1);
        final var nodeC = nodeOps.add(new TestData("NodeC"), timestamp1);

        // Connect A -> B and B -> C
        final var edgeAB = edgeOps.add(nodeA, nodeB, new TestData("EdgeAB"), timestamp2);
        final var edgeBC = edgeOps.add(nodeB, nodeC, new TestData("EdgeBC"), timestamp2);

        // Update node B (should recreate edges)
        final var updatedNodeB = nodeOps.update(nodeB.locator().id(), new TestData("UpdatedNodeB"), timestamp3);

        // Verify node B has new version
        assertEquals(2, updatedNodeB.locator().version());
        assertEquals("UpdatedNodeB", updatedNodeB.data().value());

        // Verify old edges are no longer active (they were expired during the update)
        final var activeEdgeAB = edgeOps.findActive(edgeAB.locator().id());
        final var activeEdgeBC = edgeOps.findActive(edgeBC.locator().id());

        // Should either not be found or be expired
        assertTrue(activeEdgeAB.isEmpty() || activeEdgeAB.get().expired().isPresent());
        assertTrue(activeEdgeBC.isEmpty() || activeEdgeBC.get().expired().isPresent());

        // Verify new edges are active and point to updated node
        final var activeEdgesFromA = edgeOps.allActive().stream()
                .filter(e -> e.source().locator().id().equals(nodeA.locator().id()))
                .toList();
        final var activeEdgesToC = edgeOps.allActive().stream()
                .filter(e -> e.target().locator().id().equals(nodeC.locator().id()))
                .toList();

        assertEquals(1, activeEdgesFromA.size());
        assertEquals(1, activeEdgesToC.size());
        assertEquals(
                updatedNodeB.locator().id(),
                activeEdgesFromA.getFirst().target().locator().id());
        assertEquals(
                updatedNodeB.locator().id(),
                activeEdgesToC.getFirst().source().locator().id());
    }

    @Test
    @DisplayName("Expired node has all connected edges expired")
    final void testExpiredNodeHasAllEdgesExpired() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes A, B, C
        final var nodeA = nodeOps.add(new TestData("NodeA"), timestamp1);
        final var nodeB = nodeOps.add(new TestData("NodeB"), timestamp1);
        final var nodeC = nodeOps.add(new TestData("NodeC"), timestamp1);

        // Connect A -> B and B -> C
        final var edgeAB = edgeOps.add(nodeA, nodeB, new TestData("EdgeAB"), timestamp2);
        final var edgeBC = edgeOps.add(nodeB, nodeC, new TestData("EdgeBC"), timestamp2);

        // Expire node B
        final var expiredNodeB = nodeOps.expire(nodeB.locator().id(), timestamp3);

        // Verify node B is expired
        assertTrue(expiredNodeB.expired().isPresent());
        assertEquals(timestamp3, expiredNodeB.expired().get());

        // Verify both edges are expired
        final var currentEdgeAB = edgeOps.findActive(edgeAB.locator().id());
        final var currentEdgeBC = edgeOps.findActive(edgeBC.locator().id());

        assertTrue(currentEdgeAB.isEmpty() || currentEdgeAB.get().expired().isPresent());
        assertTrue(currentEdgeBC.isEmpty() || currentEdgeBC.get().expired().isPresent());

        // Verify no active edges remain that connect to the expired node
        final var activeEdges = edgeOps.allActive();
        for (final var edge : activeEdges) {
            assertNotEquals(expiredNodeB.locator().id(), edge.source().locator().id());
            assertNotEquals(expiredNodeB.locator().id(), edge.target().locator().id());
        }
    }

    @Test
    @DisplayName("Node expiry does not cascade beyond direct connections")
    final void testNodeExpiryDoesNotCascade() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes A -> B -> C -> D
        final var nodeA = nodeOps.add(new TestData("NodeA"), timestamp1);
        final var nodeB = nodeOps.add(new TestData("NodeB"), timestamp1);
        final var nodeC = nodeOps.add(new TestData("NodeC"), timestamp1);
        final var nodeD = nodeOps.add(new TestData("NodeD"), timestamp1);

        // Connect in chain
        final var edgeAB = edgeOps.add(nodeA, nodeB, new TestData("EdgeAB"), timestamp2);
        final var edgeBC = edgeOps.add(nodeB, nodeC, new TestData("EdgeBC"), timestamp2);
        final var edgeCD = edgeOps.add(nodeC, nodeD, new TestData("EdgeCD"), timestamp2);

        // Expire node B (middle of chain)
        nodeOps.expire(nodeB.locator().id(), timestamp3);

        // Verify nodes A, C, D remain active
        assertTrue(nodeOps.findActive(nodeA.locator().id()).isPresent());
        assertTrue(nodeOps.findActive(nodeC.locator().id()).isPresent());
        assertTrue(nodeOps.findActive(nodeD.locator().id()).isPresent());

        // Verify only edges connected to B are expired (AB and BC)
        assertTrue(edgeOps.findActive(edgeAB.locator().id()).isEmpty()
                || edgeOps.findActive(edgeAB.locator().id()).get().expired().isPresent());
        assertTrue(edgeOps.findActive(edgeBC.locator().id()).isEmpty()
                || edgeOps.findActive(edgeBC.locator().id()).get().expired().isPresent());

        // Verify edge CD remains active (not connected to B)
        final var activeEdgeCD = edgeOps.findActive(edgeCD.locator().id());
        assertTrue(activeEdgeCD.isPresent());
        assertTrue(activeEdgeCD.get().expired().isEmpty());
    }

    /**
     * Simple Data implementation for testing.
     */
    private record TestData(Object value) implements Data {

        @Override
        public Class<?> type() {
            return value.getClass();
        }
    }
}
