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
 * Tests for EdgeOperations referential integrity when creating new edge versions.
 */
@DisplayName("Edge Operations Referential Integrity Tests")
public class EdgeOperationsReferentialIntegrityTest {

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
    @DisplayName("Edge update creates new version with same endpoints")
    final void testEdgeUpdatePreservesEndpoints() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create two nodes
        final Data dataA = new SimpleData(String.class, "Node A");
        final Data dataB = new SimpleData(String.class, "Node B");
        final var nodeA = nodeOps.add(dataA, timestamp1);
        final var nodeB = nodeOps.add(dataB, timestamp2);

        // Create an edge between them
        final Data edgeData1 = new SimpleData(String.class, "Edge v1");
        final var edge1 = edgeOps.add(nodeA, nodeB, edgeData1, timestamp2);

        // Update the edge
        final Data edgeData2 = new SimpleData(String.class, "Edge v2");
        final var edge2 = edgeOps.update(edge1.locator().id(), edgeData2, timestamp3);

        // Verify endpoints are preserved
        if (edge2.source() instanceof Reference.Loaded<Node> source
                && edge2.target() instanceof Reference.Loaded<Node> target) {
            assertEquals(nodeA.locator(), source.value().locator());
            assertEquals(nodeB.locator(), target.value().locator());
        }

        // Verify new version has incremented version number
        assertEquals(edge1.locator().version() + 1, edge2.locator().version());

        // Verify old version is expired
        assertTrue(edge1.expired().isEmpty());
        final var retrievedEdge1 = edgeOps.find(edge1.locator());
        assertTrue(retrievedEdge1.expired().isPresent());
        assertEquals(timestamp3, retrievedEdge1.expired().get());
    }

    @Test
    @DisplayName("Edge expire marks edge as expired")
    final void testEdgeExpire() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create two nodes
        final var nodeA = nodeOps.add(new SimpleData(String.class, "A"), timestamp1);
        final var nodeB = nodeOps.add(new SimpleData(String.class, "B"), timestamp2);

        // Create edge
        final var edge = edgeOps.add(nodeA, nodeB, new SimpleData(String.class, "A->B"), timestamp2);

        // Expire the edge
        final var expiredEdge = edgeOps.expire(edge.locator().id(), timestamp3);

        // Verify edge is expired
        assertTrue(expiredEdge.expired().isPresent());
        assertEquals(timestamp3, expiredEdge.expired().get());

        // Verify no active version exists
        assertTrue(edgeOps.findActive(edge.locator().id()).isEmpty());
    }

    @Test
    @DisplayName("Multiple edge versions are tracked correctly")
    final void testMultipleEdgeVersions() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);
        final var timestamp4 = timestamp3.plusSeconds(1);

        // Create nodes
        final var nodeA = nodeOps.add(new SimpleData(String.class, "A"), timestamp1);
        final var nodeB = nodeOps.add(new SimpleData(String.class, "B"), timestamp1);

        // Create initial edge
        final var edge1 = edgeOps.add(nodeA, nodeB, new SimpleData(String.class, "v1"), timestamp1);

        // Update edge multiple times
        final var edge2 = edgeOps.update(edge1.locator().id(), new SimpleData(String.class, "v2"), timestamp2);
        final var edge3 = edgeOps.update(edge1.locator().id(), new SimpleData(String.class, "v3"), timestamp3);

        // Verify all versions exist
        final var versions = edgeOps.findVersions(edge1.locator().id());
        assertEquals(3, versions.size());

        // Verify only latest is active
        final var active = edgeOps.findActive(edge1.locator().id());
        assertTrue(active.isPresent());
        assertEquals(edge3.locator(), active.get().locator());

        // Verify version history
        assertEquals(1, versions.get(0).locator().version());
        assertEquals(2, versions.get(1).locator().version());
        assertEquals(3, versions.get(2).locator().version());
    }

    @Test
    @DisplayName("Edge operations handle concurrent edges between same nodes")
    final void testConcurrentEdges() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);

        // Create nodes
        final var nodeA = nodeOps.add(new SimpleData(String.class, "A"), timestamp1);
        final var nodeB = nodeOps.add(new SimpleData(String.class, "B"), timestamp1);

        // Create multiple edges between same nodes
        final var edge1 = edgeOps.add(nodeA, nodeB, new SimpleData(String.class, "Edge 1"), timestamp1);
        final var edge2 = edgeOps.add(nodeA, nodeB, new SimpleData(String.class, "Edge 2"), timestamp2);

        // Verify both edges exist and are active
        assertTrue(edgeOps.findActive(edge1.locator().id()).isPresent());
        assertTrue(edgeOps.findActive(edge2.locator().id()).isPresent());

        // Verify they have different IDs
        assertFalse(edge1.locator().id().equals(edge2.locator().id()));

        // Verify both connect same nodes
        final var activeEdges = getAllActiveEdges();
        final var edgesFromAToB = activeEdges.stream()
                .filter(e -> {
                    if (e.source() instanceof Reference.Loaded<Node> source
                            && e.target() instanceof Reference.Loaded<Node> target) {
                        return source.value().equals(nodeA) && target.value().equals(nodeB);
                    }
                    return false;
                })
                .toList();
        assertEquals(2, edgesFromAToB.size());
    }

    private List<Edge> getAllActiveEdges() {
        final var allEdges = new ArrayList<Edge>();
        for (var node : nodeOps.activeNodes()) {
            allEdges.addAll(edgeOps.getEdgesFor(node));
        }
        return allEdges.stream().distinct().toList();
    }
}
