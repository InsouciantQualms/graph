/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.tinkerpop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.simple.SimpleComponent;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for Tinkerpop referential integrity implementation.
 */
@DisplayName("Tinkerpop Referential Integrity Tests")
class TinkerpopReferentialIntegrityManagerTest {

    private Graph graph;
    private TinkerpopNodeRepository nodeRepository;
    private TinkerpopEdgeRepository edgeRepository;
    private TinkerpopComponentRepository componentRepository;
    private TinkerpopReferentialIntegrityManager integrityManager;

    private final Type defaultType = new SimpleType("test");
    private final Type componentType = new SimpleType("component");

    @BeforeEach
    void before() {
        // Create in-memory Tinkerpop graph
        graph = TinkerGraph.open();

        nodeRepository = new TinkerpopNodeRepository(graph);
        edgeRepository = new TinkerpopEdgeRepository(graph, nodeRepository);
        componentRepository = new TinkerpopComponentRepository(graph, nodeRepository, edgeRepository);

        integrityManager =
                new TinkerpopReferentialIntegrityManager(graph, nodeRepository, edgeRepository, componentRepository);
    }

    @AfterEach
    void after() {
        try {
            graph.close();
        } catch (Exception e) {
            // Ignore close errors
        }
    }

    @Test
    @DisplayName("Node expiration cascades to all connected edges")
    void testNodeExpirationCascadesToEdges() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes
        final var node1 = createAndSaveNode("node1", timestamp1);
        final var node2 = createAndSaveNode("node2", timestamp1);
        final var node3 = createAndSaveNode("node3", timestamp1);

        // Create edges: node1 -> node2, node1 -> node3, node3 -> node1
        final var edge1 = createAndSaveEdge("edge1", node1, node2, timestamp2);
        final var edge2 = createAndSaveEdge("edge2", node1, node3, timestamp2);
        final var edge3 = createAndSaveEdge("edge3", node3, node1, timestamp2);

        // Expire node1
        nodeRepository.expire(node1.locator().id(), timestamp3);
        integrityManager.handleNodeExpiry(node1.locator().id(), timestamp3);

        // Verify all edges connected to node1 are expired
        final var foundEdge1 = edgeRepository.findActive(edge1.locator().id());
        final var foundEdge2 = edgeRepository.findActive(edge2.locator().id());
        final var foundEdge3 = edgeRepository.findActive(edge3.locator().id());

        assertTrue(foundEdge1.isEmpty(), "Edge1 should be expired");
        assertTrue(foundEdge2.isEmpty(), "Edge2 should be expired");
        assertTrue(foundEdge3.isEmpty(), "Edge3 should be expired");

        // Verify the expired edges have correct timestamp
        final var expiredEdge1 = edgeRepository.find(edge1.locator());
        assertTrue(expiredEdge1.expired().isPresent());
        assertEquals(timestamp3, expiredEdge1.expired().get());
    }

    @Test
    @DisplayName("Node update recreates connected edges with new node version")
    void testNodeUpdateRecreatesEdges() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create nodes
        final var node1 = createAndSaveNode("node1", timestamp1);
        final var node2 = createAndSaveNode("node2", timestamp1);

        // Create edge: node1 -> node2
        final var edge = createAndSaveEdge("edge1", node1, node2, timestamp2);

        // Update node1
        final var updatedNode1 = new SimpleNode(
                new Locator(node1.locator().id(), 2),
                defaultType,
                new TestData("UpdatedNode1"),
                new HashSet<>(),
                timestamp3,
                Optional.empty());

        nodeRepository.expire(node1.locator().id(), timestamp3);
        nodeRepository.save(updatedNode1);
        integrityManager.handleNodeUpdate(node1, updatedNode1, timestamp3);

        // Verify original edge is expired
        final var originalEdge = edgeRepository.find(edge.locator());
        assertTrue(originalEdge.expired().isPresent());
        assertEquals(timestamp3, originalEdge.expired().get());

        // Verify new edge exists with updated node reference
        final var activeEdges = edgeRepository.findVersions(edge.locator().id());
        assertEquals(2, activeEdges.size(), "Should have 2 versions of the edge");

        final var newEdge = activeEdges.stream()
                .filter(e -> e.expired().isEmpty())
                .findFirst()
                .orElseThrow();

        assertEquals(2, newEdge.locator().version());
        assertEquals(updatedNode1.locator(), newEdge.source().locator());
        assertEquals(node2.locator(), newEdge.target().locator());
    }

    @Test
    @DisplayName("Component update cascades to all referencing elements")
    void testComponentUpdateCascadesToElements() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create component
        final var component = createAndSaveComponent("comp1", timestamp1);
        final var componentSet = Set.of(component.locator());

        // Create nodes referencing the component
        final var node1 = createAndSaveNodeWithComponents("node1", componentSet, timestamp1);
        final var node2 = createAndSaveNodeWithComponents("node2", componentSet, timestamp1);

        // Create edge referencing the component
        final var edge = createAndSaveEdgeWithComponents("edge1", node1, node2, componentSet, timestamp2);

        // Update component
        final var updatedComponent = new SimpleComponent(
                new Locator(component.locator().id(), 2),
                componentType,
                new TestData("UpdatedComponent"),
                timestamp3,
                Optional.empty());

        componentRepository.expire(component.locator().id(), timestamp3);
        componentRepository.save(updatedComponent);
        integrityManager.handleComponentUpdate(component, updatedComponent, timestamp3);

        // Verify nodes were updated to reference new component version
        final var activeNode1 = nodeRepository.findActive(node1.locator().id()).orElseThrow();
        final var activeNode2 = nodeRepository.findActive(node2.locator().id()).orElseThrow();

        assertEquals(2, activeNode1.locator().version());
        assertEquals(2, activeNode2.locator().version());
        assertTrue(activeNode1.components().contains(updatedComponent.locator()));
        assertTrue(activeNode2.components().contains(updatedComponent.locator()));
        assertFalse(activeNode1.components().contains(component.locator()));
        assertFalse(activeNode2.components().contains(component.locator()));

        // Verify edge was updated to reference new component version
        final var activeEdge = edgeRepository.findActive(edge.locator().id()).orElseThrow();
        assertEquals(2, activeEdge.locator().version());
        assertTrue(activeEdge.components().contains(updatedComponent.locator()));
        assertFalse(activeEdge.components().contains(component.locator()));
    }

    @Test
    @DisplayName("Timestamp consistency across cascading operations")
    void testTimestampConsistency() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var updateTimestamp = timestamp1.plusSeconds(2);

        // Create component
        final var component = createAndSaveComponent("comp1", timestamp1);
        final var componentSet = Set.of(component.locator());

        // Create nodes with component reference
        final var node1 = createAndSaveNodeWithComponents("node1", componentSet, timestamp1);
        final var node2 = createAndSaveNodeWithComponents("node2", componentSet, timestamp1);

        // Create edge between nodes
        final var edge = createAndSaveEdgeWithComponents("edge1", node1, node2, componentSet, timestamp2);

        // Update component (should trigger cascade)
        final var updatedComponent = new SimpleComponent(
                new Locator(component.locator().id(), 2),
                componentType,
                new TestData("UpdatedComponent"),
                updateTimestamp,
                Optional.empty());

        componentRepository.expire(component.locator().id(), updateTimestamp);
        componentRepository.save(updatedComponent);
        integrityManager.handleComponentUpdate(component, updatedComponent, updateTimestamp);

        // Verify all updates have the same timestamp
        final var updatedNode1 = nodeRepository.findActive(node1.locator().id()).orElseThrow();
        final var updatedNode2 = nodeRepository.findActive(node2.locator().id()).orElseThrow();
        final var updatedEdge = edgeRepository.findActive(edge.locator().id()).orElseThrow();

        assertEquals(updateTimestamp, updatedNode1.created());
        assertEquals(updateTimestamp, updatedNode2.created());
        assertEquals(updateTimestamp, updatedEdge.created());

        // Verify expired versions also have consistent timestamp
        final var expiredNode1 = nodeRepository.find(node1.locator());
        final var expiredNode2 = nodeRepository.find(node2.locator());
        final var expiredEdge = edgeRepository.find(edge.locator());

        assertEquals(updateTimestamp, expiredNode1.expired().get());
        assertEquals(updateTimestamp, expiredNode2.expired().get());
        assertEquals(updateTimestamp, expiredEdge.expired().get());
    }

    @Test
    @DisplayName("Complex graph maintains referential integrity")
    void testComplexGraphReferentialIntegrity() {
        final var timestamp = Instant.now();

        // Create components
        final var comp1 = createAndSaveComponent("comp1", timestamp);
        final var comp2 = createAndSaveComponent("comp2", timestamp);

        // Create nodes with different component combinations
        final var nodeA = createAndSaveNodeWithComponents("nodeA", Set.of(comp1.locator()), timestamp);
        final var nodeB = createAndSaveNodeWithComponents("nodeB", Set.of(comp1.locator(), comp2.locator()), timestamp);
        final var nodeC = createAndSaveNodeWithComponents("nodeC", Set.of(comp2.locator()), timestamp);
        final var nodeD = createAndSaveNode("nodeD", timestamp); // No components

        // Create edges forming a diamond pattern
        final var edgeAB = createAndSaveEdge("edgeAB", nodeA, nodeB, timestamp);
        final var edgeAC = createAndSaveEdge("edgeAC", nodeA, nodeC, timestamp);
        final var edgeBD = createAndSaveEdgeWithComponents("edgeBD", nodeB, nodeD, Set.of(comp1.locator()), timestamp);
        final var edgeCD = createAndSaveEdge("edgeCD", nodeC, nodeD, timestamp);

        // Update comp1 (should affect nodeA, nodeB, and edgeBD)
        final var updateTime = timestamp.plusSeconds(1);
        final var updatedComp1 = new SimpleComponent(
                new Locator(comp1.locator().id(), 2),
                componentType,
                new TestData("UpdatedComp1"),
                updateTime,
                Optional.empty());

        componentRepository.expire(comp1.locator().id(), updateTime);
        componentRepository.save(updatedComp1);
        integrityManager.handleComponentUpdate(comp1, updatedComp1, updateTime);

        // Verify only affected elements were updated
        final var currentNodeA = nodeRepository.findActive(nodeA.locator().id()).orElseThrow();
        final var currentNodeB = nodeRepository.findActive(nodeB.locator().id()).orElseThrow();
        final var currentNodeC = nodeRepository.findActive(nodeC.locator().id()).orElseThrow();
        final var currentNodeD = nodeRepository.findActive(nodeD.locator().id()).orElseThrow();

        assertEquals(2, currentNodeA.locator().version(), "NodeA should be updated");
        assertEquals(2, currentNodeB.locator().version(), "NodeB should be updated");
        assertEquals(1, currentNodeC.locator().version(), "NodeC should not be updated");
        assertEquals(1, currentNodeD.locator().version(), "NodeD should not be updated");

        // Verify edge updates cascaded correctly
        final var currentEdgeAB =
                edgeRepository.findActive(edgeAB.locator().id()).orElseThrow();
        final var currentEdgeAC =
                edgeRepository.findActive(edgeAC.locator().id()).orElseThrow();
        final var currentEdgeBD =
                edgeRepository.findActive(edgeBD.locator().id()).orElseThrow();
        final var currentEdgeCD =
                edgeRepository.findActive(edgeCD.locator().id()).orElseThrow();

        assertEquals(2, currentEdgeAB.locator().version(), "EdgeAB should be updated (nodeA updated)");
        assertEquals(2, currentEdgeAC.locator().version(), "EdgeAC should be updated (nodeA updated)");
        assertEquals(2, currentEdgeBD.locator().version(), "EdgeBD should be updated (has comp1 + nodeB updated)");
        assertEquals(1, currentEdgeCD.locator().version(), "EdgeCD should not be updated");
    }

    // Helper methods

    private Node createAndSaveNode(final String id, final Instant timestamp) {
        final var locator = new Locator(NanoId.generate(), 1);
        final var node =
                new SimpleNode(locator, defaultType, new TestData(id), new HashSet<>(), timestamp, Optional.empty());
        return nodeRepository.save(node);
    }

    private Node createAndSaveNodeWithComponents(
            final String id, final Set<Locator> components, final Instant timestamp) {
        final var locator = new Locator(NanoId.generate(), 1);
        final var node = new SimpleNode(
                locator, defaultType, new TestData(id), new HashSet<>(components), timestamp, Optional.empty());
        return nodeRepository.save(node);
    }

    private Edge createAndSaveEdge(final String id, final Node source, final Node target, final Instant timestamp) {
        return createAndSaveEdgeWithComponents(id, source, target, new HashSet<>(), timestamp);
    }

    private Edge createAndSaveEdgeWithComponents(
            final String id,
            final Node source,
            final Node target,
            final Set<Locator> components,
            final Instant timestamp) {
        final var locator = new Locator(NanoId.generate(), 1);
        final var edge = new SimpleEdge(
                locator,
                defaultType,
                source,
                target,
                new TestData(id),
                new HashSet<>(components),
                timestamp,
                Optional.empty());
        return edgeRepository.save(edge);
    }

    private Component createAndSaveComponent(final String id, final Instant timestamp) {
        final var locator = new Locator(NanoId.generate(), 1);
        final var component =
                new SimpleComponent(locator, componentType, new TestData(id), timestamp, Optional.empty());
        return componentRepository.save(component);
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
