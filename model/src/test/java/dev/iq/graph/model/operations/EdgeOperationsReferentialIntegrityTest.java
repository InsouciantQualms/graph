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
import dev.iq.graph.model.jgrapht.EdgeOperations;
import dev.iq.graph.model.jgrapht.NodeOperations;
import java.time.Instant;
import org.jgrapht.graph.DefaultListenableGraph;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for EdgeOperations referential integrity when creating new edge versions.
 */
@DisplayName("Edge Operations Referential Integrity Tests")
public class EdgeOperationsReferentialIntegrityTest {

    private NodeOperations nodeOps;
    private EdgeOperations edgeOps;

    @BeforeEach
    final void before() {

        final var base = new DirectedMultigraph<Node, Edge>(null, null, false);
        final var graph = new DefaultListenableGraph<>(base);

        edgeOps = new EdgeOperations(graph);
        nodeOps = new NodeOperations(graph, edgeOps);
    }

    @Test
    @DisplayName("Edge update creates new version with same endpoints")
    final void testEdgeUpdatePreservesEndpoints() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create two nodes
        final var nodeA = nodeOps.add(new TestData("NodeA"), timestamp1);
        final var nodeB = nodeOps.add(new TestData("NodeB"), timestamp1);

        // Create edge A -> B
        final var originalEdge = edgeOps.add(nodeA, nodeB, new TestData("OriginalEdge"), timestamp2);

        // Update edge data
        final var updatedEdge = edgeOps.update(originalEdge.locator().id(), new TestData("UpdatedEdge"), timestamp3);

        // Verify edge has new version
        assertEquals(2, updatedEdge.locator().version());
        assertEquals("UpdatedEdge", updatedEdge.data().value());

        // Verify endpoints remain the same
        assertEquals(nodeA, updatedEdge.source());
        assertEquals(nodeB, updatedEdge.target());

        // Verify that the currently active edge is the updated version, not the original
        final var currentActiveEdge = edgeOps.findActive(originalEdge.locator().id());
        assertTrue(currentActiveEdge.isPresent(), "Should have an active edge");
        assertEquals(2, currentActiveEdge.get().locator().version(), "Active edge should be version 2");
        assertTrue(currentActiveEdge.get().expired().isEmpty(), "Active edge should not be expired");
        assertEquals("UpdatedEdge", currentActiveEdge.get().data().value(), "Active edge should have updated data");

        // Verify we can find the original edge at its timestamp
        final var originalEdgeAtTime = edgeOps.findAt(originalEdge.locator().id(), timestamp2);
        assertTrue(originalEdgeAtTime.isPresent());
        assertEquals("OriginalEdge", originalEdgeAtTime.get().data().value());
    }

    @Test
    @DisplayName("Multiple edge updates maintain version history")
    final void testMultipleEdgeUpdatesVersionHistory() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);
        final var timestamp4 = timestamp3.plusSeconds(1);
        final var timestamp5 = timestamp4.plusSeconds(1);

        // Create nodes
        final var nodeA = nodeOps.add(new TestData("NodeA"), timestamp1);
        final var nodeB = nodeOps.add(new TestData("NodeB"), timestamp1);

        // Create and update edge multiple times
        final var edge1 = edgeOps.add(nodeA, nodeB, new TestData("Version1"), timestamp2);
        final var edge2 = edgeOps.update(edge1.locator().id(), new TestData("Version2"), timestamp3);
        final var edge3 = edgeOps.update(edge1.locator().id(), new TestData("Version3"), timestamp4);

        // Verify version progression
        assertEquals(1, edge1.locator().version());
        assertEquals(2, edge2.locator().version());
        assertEquals(3, edge3.locator().version());

        // Verify all versions share same ID
        assertEquals(edge1.locator().id(), edge2.locator().id());
        assertEquals(edge1.locator().id(), edge3.locator().id());

        // Verify we can retrieve all versions
        final var allVersions = edgeOps.findVersions(edge1.locator().id());
        assertEquals(3, allVersions.size());

        // Verify only the latest version is active
        final var activeEdge = edgeOps.findActive(edge1.locator().id());
        assertTrue(activeEdge.isPresent());
        assertEquals(3, activeEdge.get().locator().version());
        assertEquals("Version3", activeEdge.get().data().value());

        // Verify we can find specific versions at their timestamps
        final var edgeAtT2 = edgeOps.findAt(edge1.locator().id(), timestamp2);
        final var edgeAtT3 = edgeOps.findAt(edge1.locator().id(), timestamp3);
        final var edgeAtT4 = edgeOps.findAt(edge1.locator().id(), timestamp4);

        assertTrue(edgeAtT2.isPresent());
        assertTrue(edgeAtT3.isPresent());
        assertTrue(edgeAtT4.isPresent());

        assertEquals("Version1", edgeAtT2.get().data().value());
        assertEquals("Version2", edgeAtT3.get().data().value());
        assertEquals("Version3", edgeAtT4.get().data().value());
    }

    @Test
    @DisplayName("Edge expiry maintains referential integrity")
    final void testEdgeExpiryMaintainsIntegrity() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes and edges
        final var nodeA = nodeOps.add(new TestData("NodeA"), timestamp1);
        final var nodeB = nodeOps.add(new TestData("NodeB"), timestamp1);
        final var nodeC = nodeOps.add(new TestData("NodeC"), timestamp1);

        final var edgeAB = edgeOps.add(nodeA, nodeB, new TestData("EdgeAB"), timestamp2);
        final var edgeBC = edgeOps.add(nodeB, nodeC, new TestData("EdgeBC"), timestamp2);

        // Expire one edge
        final var expiredEdgeAB = edgeOps.expire(edgeAB.locator().id(), timestamp3);

        // Verify expired edge
        assertTrue(expiredEdgeAB.expired().isPresent());
        assertEquals(timestamp3, expiredEdgeAB.expired().get());

        // Verify nodes remain active
        assertTrue(nodeOps.findActive(nodeA.locator().id()).isPresent());
        assertTrue(nodeOps.findActive(nodeB.locator().id()).isPresent());
        assertTrue(nodeOps.findActive(nodeC.locator().id()).isPresent());

        // Verify other edge remains active
        final var activeEdgeBC = edgeOps.findActive(edgeBC.locator().id());
        assertTrue(activeEdgeBC.isPresent());
        assertTrue(activeEdgeBC.get().expired().isEmpty());

        // Verify expired edge is not in active edges list
        final var activeEdges = edgeOps.allActive();
        assertFalse(activeEdges.contains(expiredEdgeAB));
        assertTrue(activeEdges.contains(activeEdgeBC.get()));
    }

    @Test
    @DisplayName("Edge versions maintain consistent endpoints across updates")
    final void testEdgeVersionsConsistentEndpoints() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);
        final var timestamp4 = timestamp3.plusSeconds(1);

        // Create nodes
        final var nodeA = nodeOps.add(new TestData("NodeA"), timestamp1);
        final var nodeB = nodeOps.add(new TestData("NodeB"), timestamp1);

        // Create edge and update it multiple times
        final var edge1 = edgeOps.add(nodeA, nodeB, new TestData("Data1"), timestamp2);
        final var edge2 = edgeOps.update(edge1.locator().id(), new TestData("Data2"), timestamp3);
        final var edge3 = edgeOps.update(edge1.locator().id(), new TestData("Data3"), timestamp4);

        // Verify all versions have same endpoints
        assertEquals(nodeA, edge1.source());
        assertEquals(nodeB, edge1.target());
        assertEquals(nodeA, edge2.source());
        assertEquals(nodeB, edge2.target());
        assertEquals(nodeA, edge3.source());
        assertEquals(nodeB, edge3.target());

        // Verify endpoints are preserved in historical queries
        final var allVersions = edgeOps.findVersions(edge1.locator().id());
        for (final var version : allVersions) {
            assertEquals(nodeA, version.source());
            assertEquals(nodeB, version.target());
        }
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
