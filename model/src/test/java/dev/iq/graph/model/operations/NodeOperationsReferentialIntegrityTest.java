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
 * Tests for NodeOperations referential integrity when creating new node versions.
 * Tests the rules:
 * - When a node expires, all connected edges must also expire
 * - When a node updates, connected edges are recreated to point to the new version
 * - All related operations use the same timestamp
 */
@DisplayName("Node Operations Referential Integrity Tests")
public class NodeOperationsReferentialIntegrityTest {

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
    @DisplayName("Node update preserves connections to new version")
    final void testNodeUpdatePreservesConnections() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create three nodes: A, B, C
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp1);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp1);
        final var nodeC = nodeOps.add(defaultType, new TestData("NodeC"), new HashSet<>(), timestamp1);

        // Connect A -> B and B -> C
        final var edgeAB = edgeOps.add(defaultType, nodeA, nodeB, new TestData("EdgeAB"), new HashSet<>(), timestamp2);
        final var edgeBC = edgeOps.add(defaultType, nodeB, nodeC, new TestData("EdgeBC"), new HashSet<>(), timestamp2);

        // Update node B (should recreate edges)
        final var updatedNodeB = nodeOps.update(nodeB.locator().id(), new TestData("UpdatedNodeB"), timestamp3);

        // Verify node B has new version
        assertEquals(2, updatedNodeB.locator().version());
        assertEquals("UpdatedNodeB", updatedNodeB.data().value());

        // Verify old edges are expired with same timestamp
        final var expiredEdges = graph.edgeSet().stream()
                .filter(e -> e.expired().isPresent() && e.expired().get().equals(timestamp3))
                .toList();
        assertEquals(2, expiredEdges.size(), "Both original edges should be expired");

        // Verify new edges are active and point to updated node
        final var activeEdges =
                graph.edgeSet().stream().filter(e -> e.expired().isEmpty()).toList();
        assertEquals(2, activeEdges.size(), "Two new edges should be active");

        // Check that new edges connect to the updated node B
        final var edgesFromA = activeEdges.stream()
                .filter(e -> e.source().locator().id().equals(nodeA.locator().id()))
                .toList();
        final var edgesToC = activeEdges.stream()
                .filter(e -> e.target().locator().id().equals(nodeC.locator().id()))
                .toList();

        assertEquals(1, edgesFromA.size());
        assertEquals(1, edgesToC.size());
        assertEquals(updatedNodeB.locator(), edgesFromA.get(0).target().locator());
        assertEquals(updatedNodeB.locator(), edgesToC.get(0).source().locator());

        // Verify all new elements have the same timestamp
        assertEquals(timestamp3, updatedNodeB.created());
        activeEdges.forEach(edge -> assertEquals(timestamp3, edge.created()));
    }

    @Test
    @DisplayName("Expired node has all connected edges expired")
    final void testExpiredNodeHasAllEdgesExpired() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes A, B, C
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp1);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp1);
        final var nodeC = nodeOps.add(defaultType, new TestData("NodeC"), new HashSet<>(), timestamp1);

        // Connect A -> B and B -> C
        final var edgeAB = edgeOps.add(defaultType, nodeA, nodeB, new TestData("EdgeAB"), new HashSet<>(), timestamp2);
        final var edgeBC = edgeOps.add(defaultType, nodeB, nodeC, new TestData("EdgeBC"), new HashSet<>(), timestamp2);

        // Expire node B
        final var expiredNodeB = nodeOps.expire(nodeB.locator().id(), timestamp3);

        // Verify node B is expired
        assertTrue(expiredNodeB.expired().isPresent());
        assertEquals(timestamp3, expiredNodeB.expired().get());

        // Verify both edges are expired with the same timestamp
        final var allEdges = graph.edgeSet();
        assertEquals(2, allEdges.size(), "Should still have 2 edges in graph");

        allEdges.forEach(edge -> {
            assertTrue(edge.expired().isPresent(), "All edges should be expired");
            assertEquals(timestamp3, edge.expired().get(), "All edges should expire at same time as node");
        });

        // Verify edges still point to the expired node (not removed from graph)
        final var edgesConnectedToB = allEdges.stream()
                .filter(e -> e.source().locator().equals(expiredNodeB.locator())
                        || e.target().locator().equals(expiredNodeB.locator()))
                .count();
        assertEquals(2, edgesConnectedToB, "Expired edges should still reference expired node");
    }

    @Test
    @DisplayName("Node expiry does not cascade beyond direct connections")
    final void testNodeExpiryDoesNotCascade() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes A -> B -> C -> D
        final var nodeA = nodeOps.add(defaultType, new TestData("NodeA"), new HashSet<>(), timestamp1);
        final var nodeB = nodeOps.add(defaultType, new TestData("NodeB"), new HashSet<>(), timestamp1);
        final var nodeC = nodeOps.add(defaultType, new TestData("NodeC"), new HashSet<>(), timestamp1);
        final var nodeD = nodeOps.add(defaultType, new TestData("NodeD"), new HashSet<>(), timestamp1);

        // Connect in chain
        final var edgeAB = edgeOps.add(defaultType, nodeA, nodeB, new TestData("EdgeAB"), new HashSet<>(), timestamp2);
        final var edgeBC = edgeOps.add(defaultType, nodeB, nodeC, new TestData("EdgeBC"), new HashSet<>(), timestamp2);
        final var edgeCD = edgeOps.add(defaultType, nodeC, nodeD, new TestData("EdgeCD"), new HashSet<>(), timestamp2);

        // Expire node B (middle of chain)
        nodeOps.expire(nodeB.locator().id(), timestamp3);

        // Verify only edges connected to B are expired
        final var edgeAbInGraph = graph.edgeSet().stream()
                .filter(e -> e.locator().id().equals(edgeAB.locator().id()))
                .findFirst()
                .orElseThrow();
        final var edgeBcInGraph = graph.edgeSet().stream()
                .filter(e -> e.locator().id().equals(edgeBC.locator().id()))
                .findFirst()
                .orElseThrow();
        final var edgeCdInGraph = graph.edgeSet().stream()
                .filter(e -> e.locator().id().equals(edgeCD.locator().id()))
                .findFirst()
                .orElseThrow();

        assertTrue(edgeAbInGraph.expired().isPresent());
        assertTrue(edgeBcInGraph.expired().isPresent());
        assertTrue(edgeCdInGraph.expired().isEmpty(), "Edge C->D should not be expired");

        // Verify nodes A, C, D are still active
        assertTrue(graph.vertexSet().stream()
                .anyMatch(n -> n.locator().id().equals(nodeA.locator().id())
                        && n.expired().isEmpty()));
        assertTrue(graph.vertexSet().stream()
                .anyMatch(n -> n.locator().id().equals(nodeC.locator().id())
                        && n.expired().isEmpty()));
        assertTrue(graph.vertexSet().stream()
                .anyMatch(n -> n.locator().id().equals(nodeD.locator().id())
                        && n.expired().isEmpty()));
    }

    @Test
    @DisplayName("Node update with components preserves component references")
    final void testNodeUpdateWithComponents() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);

        // Create node with components
        final Set<Locator> components = new HashSet<>();
        components.add(Locator.generate());
        components.add(Locator.generate());

        final var node = nodeOps.add(defaultType, new TestData("Node"), components, timestamp1);

        // Update node data (components should be preserved)
        final var updatedNode = nodeOps.update(node.locator().id(), new TestData("UpdatedNode"), timestamp2);

        assertEquals(components, updatedNode.components(), "Components should be preserved on update");
        assertEquals(2, updatedNode.locator().version());
    }

    @Test
    @DisplayName("Node update components creates new version")
    final void testNodeUpdateComponents() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);

        // Create node with initial components
        final Set<Locator> initialComponents = new HashSet<>();
        initialComponents.add(Locator.generate());

        final var node = nodeOps.add(defaultType, new TestData("Node"), initialComponents, timestamp1);

        // Update node components
        final Set<Locator> newComponents = new HashSet<>();
        newComponents.add(Locator.generate());
        newComponents.add(Locator.generate());

        final var updatedNode = nodeOps.updateComponents(node.locator().id(), newComponents, timestamp2);

        assertEquals(newComponents, updatedNode.components(), "Components should be updated");
        assertEquals(2, updatedNode.locator().version());
        assertEquals("Node", updatedNode.data().value(), "Data should be preserved");
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
