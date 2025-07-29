/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Reference;
import dev.iq.graph.model.jgrapht.EdgeOperations;
import dev.iq.graph.model.jgrapht.NodeOperations;
import dev.iq.graph.model.simple.SimpleData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jgrapht.graph.DefaultListenableGraph;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for referential integrity in node operations.
 */
class NodeOperationsReferentialIntegrityTest {

    private EdgeOperations edgeOps;
    private NodeOperations nodeOps;

    @BeforeEach
    final void before() {

        final var base = new DirectedMultigraph<Reference<Node>, Reference<Edge>>(null, null, false);
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

        // Create nodes
        final Data dataA = new SimpleData(String.class, "A");
        final Data dataB = new SimpleData(String.class, "B");
        final Data dataC = new SimpleData(String.class, "C");
        final Data dataBUpdated = new SimpleData(String.class, "B-updated");

        final var nodeA = nodeOps.add(dataA, timestamp1);
        final var nodeB = nodeOps.add(dataB, timestamp1);
        final var nodeC = nodeOps.add(dataC, timestamp1);

        // Create edges: A -> B -> C
        final var edgeAB = edgeOps.add(nodeA, nodeB, new SimpleData(String.class, "A->B"), timestamp1);
        final var edgeBC = edgeOps.add(nodeB, nodeC, new SimpleData(String.class, "B->C"), timestamp2);

        // Update node B
        final var updatedNodeB = nodeOps.update(nodeB.locator().id(), dataBUpdated, timestamp3);

        // Verify the original edges are expired
        final var activeEdgeAB = edgeOps.findActive(edgeAB.locator().id());
        final var activeEdgeBC = edgeOps.findActive(edgeBC.locator().id());

        // Should either not be found or be expired
        assertTrue(activeEdgeAB.isEmpty() || activeEdgeAB.get().expired().isPresent());
        assertTrue(activeEdgeBC.isEmpty() || activeEdgeBC.get().expired().isPresent());

        // Verify new edges are active and point to updated node
        final var activeEdgesFromA = getAllActiveEdges().stream()
                .filter(e -> {
                    if (e.source() instanceof Reference.Loaded<Node> loaded) {
                        return loaded.value()
                                .locator()
                                .id()
                                .equals(nodeA.locator().id());
                    }
                    return false;
                })
                .toList();
        final var activeEdgesToC = getAllActiveEdges().stream()
                .filter(e -> {
                    if (e.target() instanceof Reference.Loaded<Node> loaded) {
                        return loaded.value()
                                .locator()
                                .id()
                                .equals(nodeC.locator().id());
                    }
                    return false;
                })
                .toList();

        assertEquals(1, activeEdgesFromA.size());
        assertEquals(1, activeEdgesToC.size());

        // Verify the new edges point to the updated node version
        final var newEdgeFromA = activeEdgesFromA.get(0);
        final var newEdgeToC = activeEdgesToC.get(0);

        if (newEdgeFromA.target() instanceof Reference.Loaded<Node> loaded) {
            assertEquals(updatedNodeB.locator(), loaded.value().locator());
        }
        if (newEdgeToC.source() instanceof Reference.Loaded<Node> loaded) {
            assertEquals(updatedNodeB.locator(), loaded.value().locator());
        }
    }

    @Test
    @DisplayName("Node expiry expires all connected edges")
    final void testNodeExpiryExpiresEdges() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);

        // Create three nodes connected as A -> B -> C
        final var nodeA = nodeOps.add(new SimpleData(String.class, "A"), timestamp1);
        final var nodeB = nodeOps.add(new SimpleData(String.class, "B"), timestamp1);
        final var nodeC = nodeOps.add(new SimpleData(String.class, "C"), timestamp1);

        final var edgeAB = edgeOps.add(nodeA, nodeB, new SimpleData(String.class, "A->B"), timestamp1);
        final var edgeBC = edgeOps.add(nodeB, nodeC, new SimpleData(String.class, "B->C"), timestamp1);

        // Expire middle node B
        final var expiredNodeB = nodeOps.expire(nodeB.locator().id(), timestamp2);

        // Verify the edges are also expired
        final var currentEdgeAB = edgeOps.findActive(edgeAB.locator().id());
        final var currentEdgeBC = edgeOps.findActive(edgeBC.locator().id());

        assertTrue(currentEdgeAB.isEmpty() || currentEdgeAB.get().expired().isPresent());
        assertTrue(currentEdgeBC.isEmpty() || currentEdgeBC.get().expired().isPresent());

        // Verify no active edges remain that connect to the expired node
        final var activeEdges = getAllActiveEdges();
        for (final var edge : activeEdges) {
            if (edge.source() instanceof Reference.Loaded<Node> loaded) {
                assertNotEquals(
                        expiredNodeB.locator().id(), loaded.value().locator().id());
            }
            if (edge.target() instanceof Reference.Loaded<Node> loaded) {
                assertNotEquals(
                        expiredNodeB.locator().id(), loaded.value().locator().id());
            }
        }
    }

    private List<Edge> getAllActiveEdges() {
        final var allEdges = new ArrayList<Edge>();
        for (var node : nodeOps.activeNodes()) {
            allEdges.addAll(edgeOps.getEdgesFor(node));
        }
        return allEdges.stream().distinct().toList();
    }
}
