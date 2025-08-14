/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for NodeOperations referential integrity when creating new node versions.
 * Tests the rules:
 * - When a node expires, all connected edges must also expire
 * - When a node updates, connected edges are recreated to point to the new version
 * - All related operations use the same timestamp
 */
@DisplayName("Node Operations Referential Integrity Tests")
class NodeOperationsReferentialIntegrityTest {

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
    @DisplayName("Node update preserves connections to new version")
    final void testNodeUpdatePreservesConnections() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create three nodes: A, B, C
        final Uid nodeAId = UidFactory.generate();
        final Uid nodeBId = UidFactory.generate();
        final Uid nodeCId = UidFactory.generate();

        final var nodeA = nodeOps.add(nodeAId, defaultType, new SimpleData(String.class, "NodeA"), timestamp1);
        final var nodeB = nodeOps.add(nodeBId, defaultType, new SimpleData(String.class, "NodeB"), timestamp1);
        final var nodeC = nodeOps.add(nodeCId, defaultType, new SimpleData(String.class, "NodeC"), timestamp1);

        // Connect A -> B and B -> C
        final var edgeAB = edgeOps.add(
                defaultType, nodeA, nodeB, new SimpleData(String.class, "EdgeAB"), new HashSet<>(), timestamp2);
        final var edgeBC = edgeOps.add(
                defaultType, nodeB, nodeC, new SimpleData(String.class, "EdgeBC"), new HashSet<>(), timestamp2);

        // Update node B (should recreate edges)
        final var updatedNodeB = nodeOps.update(
                nodeB.locator().id(), defaultType, new SimpleData(String.class, "UpdatedNodeB"), timestamp3);

        // Verify node B has new version
        assertEquals(2, updatedNodeB.locator().version());
        assertEquals("UpdatedNodeB", ((SimpleData) updatedNodeB.data()).value());

        // Verify original edges are expired
        final var edges = graph.edgeSet();
        final var expiredEdges =
                edges.stream().filter(e -> e.expired().isPresent()).toList();
        assertEquals(2, expiredEdges.size(), "Original edges should be expired");

        // Verify new edges exist
        final var activeEdges =
                edges.stream().filter(e -> e.expired().isEmpty()).toList();
        assertEquals(2, activeEdges.size(), "New edges should be created");

        // Verify new edges connect to updated node B
        activeEdges.forEach(edge -> {
            if (edge.source().locator().id().equals(nodeA.locator().id())) {
                // This is the recreated A -> B edge
                assertEquals(
                        updatedNodeB.locator().id(), edge.target().locator().id());
                assertEquals(2, edge.target().locator().version());
            } else if (edge.target().locator().id().equals(nodeC.locator().id())) {
                // This is the recreated B -> C edge
                assertEquals(
                        updatedNodeB.locator().id(), edge.source().locator().id());
                assertEquals(2, edge.source().locator().version());
            }
            // All recreated edges should have the update timestamp
            assertEquals(timestamp3, edge.created());
        });
    }

    @Test
    @DisplayName("Node expiration cascades to all connected edges")
    final void testNodeExpirationCascadesToEdges() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes A, B, C
        final Uid nodeAId = UidFactory.generate();
        final Uid nodeBId = UidFactory.generate();
        final Uid nodeCId = UidFactory.generate();

        final var nodeA = nodeOps.add(nodeAId, defaultType, new SimpleData(String.class, "NodeA"), timestamp1);
        final var nodeB = nodeOps.add(nodeBId, defaultType, new SimpleData(String.class, "NodeB"), timestamp1);
        final var nodeC = nodeOps.add(nodeCId, defaultType, new SimpleData(String.class, "NodeC"), timestamp1);

        // Connect A -> B and B -> C
        final var edgeAB = edgeOps.add(
                defaultType, nodeA, nodeB, new SimpleData(String.class, "EdgeAB"), new HashSet<>(), timestamp2);
        final var edgeBC = edgeOps.add(
                defaultType, nodeB, nodeC, new SimpleData(String.class, "EdgeBC"), new HashSet<>(), timestamp2);

        // Expire node B
        final var expiredNodeB = nodeOps.expire(nodeB.locator().id(), timestamp3);

        // Verify node B is expired
        assertTrue(expiredNodeB.expired().isPresent());
        assertEquals(timestamp3, expiredNodeB.expired().get());

        // Verify both edges are expired with the same timestamp
        final var allEdges = graph.edgeSet();
        assertEquals(2, allEdges.size(), "Should still have 2 edges in graph");

        allEdges.forEach(edge -> {
            assertTrue(edge.expired().isPresent(), "Edge should be expired");
            assertEquals(timestamp3, edge.expired().get(), "Edge should be expired at same timestamp as node");
        });
    }

    @Test
    @DisplayName("Node expiration with bidirectional edges")
    final void testNodeExpirationBidirectionalEdges() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes
        final Uid nodeAId = UidFactory.generate();
        final Uid nodeBId = UidFactory.generate();
        final Uid nodeCId = UidFactory.generate();

        final var nodeA = nodeOps.add(nodeAId, defaultType, new SimpleData(String.class, "NodeA"), timestamp1);
        final var nodeB = nodeOps.add(nodeBId, defaultType, new SimpleData(String.class, "NodeB"), timestamp1);
        final var nodeC = nodeOps.add(nodeCId, defaultType, new SimpleData(String.class, "NodeC"), timestamp1);

        // Create edges
        final var edgeAB = edgeOps.add(
                defaultType, nodeA, nodeB, new SimpleData(String.class, "EdgeAB"), new HashSet<>(), timestamp2);
        final var edgeBC = edgeOps.add(
                defaultType, nodeB, nodeC, new SimpleData(String.class, "EdgeBC"), new HashSet<>(), timestamp2);
        final var edgeCB = edgeOps.add(
                defaultType, nodeC, nodeB, new SimpleData(String.class, "EdgeCB"), new HashSet<>(), timestamp2);

        // Expire node B
        nodeOps.expire(nodeB.locator().id(), timestamp3);

        // All three edges should be expired
        final var expiredEdges =
                graph.edgeSet().stream().filter(e -> e.expired().isPresent()).toList();
        assertEquals(3, expiredEdges.size(), "All edges connected to B should be expired");

        expiredEdges.forEach(edge -> {
            assertEquals(timestamp3, edge.expired().get());
        });
    }

    @Test
    @DisplayName("Complex node update scenario")
    final void testComplexNodeUpdateScenario() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes A, B, C, D
        final Uid nodeAId = UidFactory.generate();
        final Uid nodeBId = UidFactory.generate();
        final Uid nodeCId = UidFactory.generate();
        final Uid nodeDId = UidFactory.generate();

        final var nodeA = nodeOps.add(nodeAId, defaultType, new SimpleData(String.class, "NodeA"), timestamp1);
        final var nodeB = nodeOps.add(nodeBId, defaultType, new SimpleData(String.class, "NodeB"), timestamp1);
        final var nodeC = nodeOps.add(nodeCId, defaultType, new SimpleData(String.class, "NodeC"), timestamp1);
        final var nodeD = nodeOps.add(nodeDId, defaultType, new SimpleData(String.class, "NodeD"), timestamp1);

        // Create edges forming a more complex structure
        final var edgeAB = edgeOps.add(
                defaultType, nodeA, nodeB, new SimpleData(String.class, "EdgeAB"), new HashSet<>(), timestamp2);
        final var edgeBC = edgeOps.add(
                defaultType, nodeB, nodeC, new SimpleData(String.class, "EdgeBC"), new HashSet<>(), timestamp2);
        final var edgeCD = edgeOps.add(
                defaultType, nodeC, nodeD, new SimpleData(String.class, "EdgeCD"), new HashSet<>(), timestamp2);
        final var edgeDB = edgeOps.add(
                defaultType, nodeD, nodeB, new SimpleData(String.class, "EdgeDB"), new HashSet<>(), timestamp2);

        // Update node B
        final var updatedNodeB = nodeOps.update(
                nodeB.locator().id(), defaultType, new SimpleData(String.class, "UpdatedNodeB"), timestamp3);

        // Verify correct number of edges are expired and recreated
        final var allEdges = graph.edgeSet();
        final var expiredEdges =
                allEdges.stream().filter(e -> e.expired().isPresent()).toList();
        final var activeEdges =
                allEdges.stream().filter(e -> e.expired().isEmpty()).toList();

        // 3 edges connected to B should be expired (AB, BC, DB)
        assertEquals(3, expiredEdges.size());
        // 4 edges should be active (3 recreated + 1 unchanged CD)
        assertEquals(4, activeEdges.size());

        // Verify CD edge is unchanged
        final var unchangedCD = activeEdges.stream()
                .filter(e -> e.source().locator().id().equals(nodeC.locator().id())
                        && e.target().locator().id().equals(nodeD.locator().id()))
                .findFirst()
                .orElseThrow();
        assertEquals(timestamp2, unchangedCD.created(), "CD edge should not be recreated");
    }

    @Test
    @DisplayName("Node update preserves data")
    final void testNodeUpdatePreservesData() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);

        // Create node
        final Uid nodeId = UidFactory.generate();
        final var node = nodeOps.add(nodeId, defaultType, new SimpleData(String.class, "Node"), timestamp1);

        // Update node with new data
        final var updatedNode = nodeOps.update(
                node.locator().id(), defaultType, new SimpleData(String.class, "UpdatedNode"), timestamp2);

        assertEquals("UpdatedNode", ((SimpleData) updatedNode.data()).value());
        assertEquals(2, updatedNode.locator().version());
    }
}
