/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.common.version.Locator;
import dev.iq.common.version.Uid;
import dev.iq.common.version.UidFactory;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.jgrapht.JGraphtEdgeOperations;
import dev.iq.graph.model.jgrapht.JGraphtNodeOperations;
import dev.iq.graph.model.simple.SimpleData;
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
class EdgeOperationsReferentialIntegrityTest {

    private Graph<Node, Edge> graph;
    private JGraphtNodeOperations nodeOps;
    private JGraphtEdgeOperations edgeOps;
    private final Type defaultType = new SimpleType("test");

    @BeforeEach
    final void before() {
        graph = new DirectedMultigraph<>(null, null, true); // Allow self-loops
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
        final Uid nodeAId = UidFactory.generate();
        final Uid nodeBId = UidFactory.generate();
        final var nodeA = nodeOps.add(nodeAId, defaultType, new SimpleData(String.class, "NodeA"), timestamp1);
        final var nodeB = nodeOps.add(nodeBId, defaultType, new SimpleData(String.class, "NodeB"), timestamp1);

        // Create edge A -> B
        final var originalEdge = edgeOps.add(
                defaultType, nodeA, nodeB, new SimpleData(String.class, "OriginalEdge"), new HashSet<>(), timestamp2);

        // Update edge data
        final var updatedEdge = edgeOps.update(
                originalEdge.locator().id(),
                defaultType,
                new SimpleData(String.class, "UpdatedEdge"),
                new HashSet<>(),
                timestamp3);

        // Verify edge has new version
        assertEquals(2, updatedEdge.locator().version());
        assertEquals("UpdatedEdge", ((SimpleData) updatedEdge.data()).value());

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
        final Uid nodeAId = UidFactory.generate();
        final Uid nodeBId = UidFactory.generate();
        final var nodeA = nodeOps.add(nodeAId, defaultType, new SimpleData(String.class, "NodeA"), timestamp1);
        final var nodeB = nodeOps.add(nodeBId, defaultType, new SimpleData(String.class, "NodeB"), timestamp1);

        final var edge = edgeOps.add(
                defaultType, nodeA, nodeB, new SimpleData(String.class, "Edge"), new HashSet<>(), timestamp2);

        // Expire edge
        final var expiredEdge = edgeOps.expire(edge.locator().id(), timestamp3);

        // Verify edge is expired
        assertTrue(expiredEdge.expired().isPresent());
        assertEquals(timestamp3, expiredEdge.expired().get());

        // Verify nodes remain unaffected
        final var currentNodeA = graph.vertexSet().stream()
                .filter(n -> n.locator().id().equals(nodeA.locator().id()))
                .findFirst()
                .orElseThrow();
        final var currentNodeB = graph.vertexSet().stream()
                .filter(n -> n.locator().id().equals(nodeB.locator().id()))
                .findFirst()
                .orElseThrow();

        assertEquals(1, currentNodeA.locator().version());
        assertEquals(1, currentNodeB.locator().version());
        assertTrue(currentNodeA.expired().isEmpty());
        assertTrue(currentNodeB.expired().isEmpty());
    }

    @Test
    @DisplayName("Edge update with components")
    final void testEdgeUpdateWithComponents() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes
        final Uid nodeAId = UidFactory.generate();
        final Uid nodeBId = UidFactory.generate();
        final var nodeA = nodeOps.add(nodeAId, defaultType, new SimpleData(String.class, "NodeA"), timestamp1);
        final var nodeB = nodeOps.add(nodeBId, defaultType, new SimpleData(String.class, "NodeB"), timestamp1);

        // Create edge with components
        final Set<Locator> initialComponents = new HashSet<>();
        initialComponents.add(Locator.generate());

        final var edge = edgeOps.add(
                defaultType, nodeA, nodeB, new SimpleData(String.class, "Edge"), initialComponents, timestamp2);

        // Update edge with new components
        final Set<Locator> newComponents = new HashSet<>();
        newComponents.add(Locator.generate());
        newComponents.add(Locator.generate());

        final var updatedEdge = edgeOps.update(
                edge.locator().id(),
                defaultType,
                new SimpleData(String.class, "UpdatedEdge"),
                newComponents,
                timestamp3);

        // Verify edge update
        assertEquals(2, updatedEdge.locator().version());
        assertEquals("UpdatedEdge", ((SimpleData) updatedEdge.data()).value());
        assertEquals(newComponents, updatedEdge.components());
        assertEquals(timestamp3, updatedEdge.created());

        // Verify old edge is expired
        final var oldEdge = graph.edgeSet().stream()
                .filter(e -> e.locator().version() == 1)
                .findFirst()
                .orElseThrow();
        assertTrue(oldEdge.expired().isPresent());
        assertEquals(timestamp3, oldEdge.expired().get());
    }

    @Test
    @DisplayName("Multiple edges between same nodes")
    final void testMultipleEdgesBetweenNodes() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);
        final var timestamp4 = timestamp3.plusSeconds(1);

        // Create nodes
        final Uid nodeAId = UidFactory.generate();
        final Uid nodeBId = UidFactory.generate();
        final var nodeA = nodeOps.add(nodeAId, defaultType, new SimpleData(String.class, "NodeA"), timestamp1);
        final var nodeB = nodeOps.add(nodeBId, defaultType, new SimpleData(String.class, "NodeB"), timestamp1);

        // Create multiple edges between same nodes
        final var edge1 = edgeOps.add(
                new SimpleType("type1"),
                nodeA,
                nodeB,
                new SimpleData(String.class, "Edge1"),
                new HashSet<>(),
                timestamp2);
        final var edge2 = edgeOps.add(
                new SimpleType("type2"),
                nodeA,
                nodeB,
                new SimpleData(String.class, "Edge2"),
                new HashSet<>(),
                timestamp3);

        // Update one edge
        final var updatedEdge1 = edgeOps.update(
                edge1.locator().id(),
                new SimpleType("type1"),
                new SimpleData(String.class, "UpdatedEdge1"),
                new HashSet<>(),
                timestamp4);

        // Verify only edge1 was updated
        assertEquals(2, updatedEdge1.locator().version());

        // Verify edge2 remains unchanged
        final var currentEdge2 = graph.edgeSet().stream()
                .filter(e -> e.locator().id().equals(edge2.locator().id()))
                .filter(e -> e.expired().isEmpty())
                .findFirst()
                .orElseThrow();
        assertEquals(1, currentEdge2.locator().version());
        assertEquals("Edge2", ((SimpleData) currentEdge2.data()).value());
    }
}
