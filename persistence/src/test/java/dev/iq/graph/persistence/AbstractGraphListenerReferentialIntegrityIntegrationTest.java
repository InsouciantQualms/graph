/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.common.persist.VersionedRepository;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.persistence.tinkerpop.TestDataHelper;
import java.time.Instant;
import org.jgrapht.event.GraphEdgeChangeEvent;
import org.jgrapht.event.GraphVertexChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public abstract class AbstractGraphListenerReferentialIntegrityIntegrationTest {

    private GraphListenerRepository listener;
    private VersionedRepository<Node> nodes;
    private VersionedRepository<Edge> edges;

    @BeforeEach
    final void before() {
        final var repository = createGraphRepository();
        nodes = repository.nodes();
        edges = repository.edges();
        listener = new DelegatedGraphListenerRepository(repository);
    }

    protected abstract GraphRepository createGraphRepository();

    @Test
    @DisplayName("Adding and retrieving nodes maintains referential integrity")
    final void testNodeAdditionMaintainsReferentialIntegrity() {
        final var timestamp = Instant.now();

        // Create test nodes with different data types
        final var stringData = new SimpleData(String.class, "test string");
        final var stringNode = TestDataHelper.createNode(NanoId.generate(), 1, stringData, timestamp);

        final var integerData = new SimpleData(Integer.class, 42);
        final var integerNode = TestDataHelper.createNode(NanoId.generate(), 1, integerData, timestamp);

        // Add nodes via listener events
        listener.vertexAdded(new GraphVertexChangeEvent<>(this, GraphVertexChangeEvent.VERTEX_ADDED, stringNode));
        listener.vertexAdded(new GraphVertexChangeEvent<>(this, GraphVertexChangeEvent.VERTEX_ADDED, integerNode));

        // Flush operations to persist
        listener.flush();

        // Verify nodes can be retrieved through the underlying repository
        final var retrievedStringNode = nodes.findActive(stringNode.locator().id());
        assertTrue(retrievedStringNode.isPresent());
        assertEquals(String.class, retrievedStringNode.get().data().type());
        assertEquals("test string", retrievedStringNode.get().data().value());

        final var retrievedIntegerNode = nodes.findActive(integerNode.locator().id());
        assertTrue(retrievedIntegerNode.isPresent());
        assertEquals(Integer.class, retrievedIntegerNode.get().data().type());
        assertEquals(42, retrievedIntegerNode.get().data().value());
    }

    @Test
    @DisplayName("Adding node versions maintains referential integrity")
    final void testNodeVersioningMaintainsReferentialIntegrity() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);

        // Create initial node
        final var nodeDataV1 = new SimpleData(String.class, "Node-v1");
        final var nodeV1 = TestDataHelper.createNode(NanoId.generate(), 1, nodeDataV1, timestamp1);

        // Add initial version
        listener.vertexAdded(new GraphVertexChangeEvent<>(this, GraphVertexChangeEvent.VERTEX_ADDED, nodeV1));
        listener.flush();

        // Create new version of same node
        final var nodeDataV2 = new SimpleData(String.class, "Node-v2");
        final var nodeV2 = TestDataHelper.createNode(nodeV1.locator().id(), 2, nodeDataV2, timestamp2);

        // Add new version
        listener.vertexAdded(new GraphVertexChangeEvent<>(this, GraphVertexChangeEvent.VERTEX_ADDED, nodeV2));
        listener.flush();

        // Verify both versions exist
        final var nodeVersions = nodes.findAll(nodeV1.locator().id());

        assertEquals(2, nodeVersions.size());
        final var versions =
                nodeVersions.stream().map(node -> node.locator().version()).toList();
        assertTrue(versions.contains(1));
        assertTrue(versions.contains(2));
    }

    @Test
    @DisplayName("Edge creation maintains referential integrity with endpoints")
    final void testEdgeCreationMaintainsReferentialIntegrity() {
        final var timestamp = Instant.now();

        // Create nodes first
        final var nodeAData = new SimpleData(String.class, "Node A");
        final var nodeA = TestDataHelper.createNode(NanoId.generate(), 1, nodeAData, timestamp);

        final var nodeBData = new SimpleData(String.class, "Node B");
        final var nodeB = TestDataHelper.createNode(NanoId.generate(), 1, nodeBData, timestamp);

        // Add nodes
        listener.vertexAdded(new GraphVertexChangeEvent<>(this, GraphVertexChangeEvent.VERTEX_ADDED, nodeA));
        listener.vertexAdded(new GraphVertexChangeEvent<>(this, GraphVertexChangeEvent.VERTEX_ADDED, nodeB));
        listener.flush();

        // Create edge
        final var edgeData = new SimpleData(String.class, "test edge");
        final var edge = TestDataHelper.createEdge(NanoId.generate(), 1, nodeA, nodeB, edgeData, timestamp);

        // Add edge
        listener.edgeAdded(new GraphEdgeChangeEvent<>(this, GraphEdgeChangeEvent.EDGE_ADDED, edge, nodeA, nodeB));
        listener.flush();

        // Verify edge exists and maintains references to correct nodes
        final var retrievedEdge = edges.findActive(edge.locator().id());
        assertTrue(retrievedEdge.isPresent());

        final var foundEdge = retrievedEdge.get();
        assertEquals(nodeA.locator().id(), foundEdge.source().locator().id());
        assertEquals(nodeB.locator().id(), foundEdge.target().locator().id());
        assertEquals("test edge", foundEdge.data().value());
    }

    @Test
    @DisplayName("Node expiration maintains referential integrity")
    final void testNodeExpirationMaintainsReferentialIntegrity() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);

        // Create and add node
        final var nodeData = new SimpleData(String.class, "test node");
        final var node = TestDataHelper.createNode(NanoId.generate(), 1, nodeData, timestamp1);

        listener.vertexAdded(new GraphVertexChangeEvent<>(this, GraphVertexChangeEvent.VERTEX_ADDED, node));
        listener.flush();

        // Expire the node
        final var expiredNode = TestDataHelper.createExpiredNode(
                node.locator().id(), node.locator().version(), nodeData, timestamp1, timestamp2);

        listener.vertexRemoved(new GraphVertexChangeEvent<>(this, GraphVertexChangeEvent.VERTEX_REMOVED, expiredNode));
        listener.flush();

        // Verify node still exists but is expired
        final var retrievedNode = nodes.find(node.locator());
        assertTrue(retrievedNode.isPresent());
        assertTrue(retrievedNode.get().expired().isPresent());
    }

    @Test
    @DisplayName("Edge expiration maintains referential integrity")
    final void testEdgeExpirationMaintainsReferentialIntegrity() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);

        // Create nodes and edge
        final var nodeAData = new SimpleData(String.class, "Node A");
        final var nodeA = TestDataHelper.createNode(NanoId.generate(), 1, nodeAData, timestamp1);

        final var nodeBData = new SimpleData(String.class, "Node B");
        final var nodeB = TestDataHelper.createNode(NanoId.generate(), 1, nodeBData, timestamp1);

        listener.vertexAdded(new GraphVertexChangeEvent<>(this, GraphVertexChangeEvent.VERTEX_ADDED, nodeA));
        listener.vertexAdded(new GraphVertexChangeEvent<>(this, GraphVertexChangeEvent.VERTEX_ADDED, nodeB));
        listener.flush();

        final var edgeData = new SimpleData(String.class, "test edge");
        final var edge = TestDataHelper.createEdge(NanoId.generate(), 1, nodeA, nodeB, edgeData, timestamp1);

        listener.edgeAdded(new GraphEdgeChangeEvent<>(this, GraphEdgeChangeEvent.EDGE_ADDED, edge, nodeA, nodeB));
        listener.flush();

        // Expire the edge
        final var expiredEdge = TestDataHelper.createExpiredEdge(
                edge.locator().id(), edge.locator().version(), nodeA, nodeB, edgeData, timestamp1, timestamp2);

        listener.edgeRemoved(
                new GraphEdgeChangeEvent<>(this, GraphEdgeChangeEvent.EDGE_REMOVED, expiredEdge, nodeA, nodeB));
        listener.flush();

        // Verify edge still exists but is expired
        final var retrievedEdge = edges.find(edge.locator());
        assertTrue(retrievedEdge.isPresent());
        assertTrue(retrievedEdge.get().expired().isPresent());
    }

    @Test
    @DisplayName("Complex graph operations maintain referential integrity")
    final void testComplexGraphOperationsMaintainReferentialIntegrity() {
        final var timestamp1 = Instant.now();
        final var timestamp2 = timestamp1.plusSeconds(1);
        final var timestamp3 = timestamp2.plusSeconds(1);

        // Create multiple nodes
        final var nodeAData = new SimpleData(String.class, "Node A");
        final var nodeA = TestDataHelper.createNode(NanoId.generate(), 1, nodeAData, timestamp1);

        final var nodeBData = new SimpleData(String.class, "Node B");
        final var nodeB = TestDataHelper.createNode(NanoId.generate(), 1, nodeBData, timestamp1);

        final var nodeCData = new SimpleData(String.class, "Node C");
        final var nodeC = TestDataHelper.createNode(NanoId.generate(), 1, nodeCData, timestamp1);

        // Add all nodes
        listener.vertexAdded(new GraphVertexChangeEvent<>(this, GraphVertexChangeEvent.VERTEX_ADDED, nodeA));
        listener.vertexAdded(new GraphVertexChangeEvent<>(this, GraphVertexChangeEvent.VERTEX_ADDED, nodeB));
        listener.vertexAdded(new GraphVertexChangeEvent<>(this, GraphVertexChangeEvent.VERTEX_ADDED, nodeC));
        listener.flush();

        // Create edges between nodes
        final var edgeAbData = new SimpleData(String.class, "Edge A->B");
        final var edgeAb = TestDataHelper.createEdge(NanoId.generate(), 1, nodeA, nodeB, edgeAbData, timestamp2);

        final var edgeBcData = new SimpleData(String.class, "Edge B->C");
        final var edgeBc = TestDataHelper.createEdge(NanoId.generate(), 1, nodeB, nodeC, edgeBcData, timestamp2);

        // Add edges
        listener.edgeAdded(new GraphEdgeChangeEvent<>(this, GraphEdgeChangeEvent.EDGE_ADDED, edgeAb, nodeA, nodeB));
        listener.edgeAdded(new GraphEdgeChangeEvent<>(this, GraphEdgeChangeEvent.EDGE_ADDED, edgeBc, nodeB, nodeC));
        listener.flush();

        // Create new version of nodeB
        final var nodeBDataV2 = new SimpleData(String.class, "Node B v2");
        final var nodeBV2 = TestDataHelper.createNode(nodeB.locator().id(), 2, nodeBDataV2, timestamp3);

        listener.vertexAdded(new GraphVertexChangeEvent<>(this, GraphVertexChangeEvent.VERTEX_ADDED, nodeBV2));
        listener.flush();

        // Verify node versions exist
        final var nodeAVersions = nodes.findAll(nodeA.locator().id());
        final var nodeBVersions = nodes.findAll(nodeB.locator().id());
        final var nodeCVersions = nodes.findAll(nodeC.locator().id());

        // Should have 1 version for nodeA and nodeC, 2 versions for nodeB
        assertEquals(1, nodeAVersions.size());
        assertEquals(2, nodeBVersions.size());
        assertEquals(1, nodeCVersions.size());

        // Verify edges exist and maintain correct relationships
        final var foundEdgeAb = edges.findActive(edgeAb.locator().id()).orElseThrow();
        final var foundEdgeBc = edges.findActive(edgeBc.locator().id()).orElseThrow();

        assertEquals(nodeA.locator().id(), foundEdgeAb.source().locator().id());
        assertEquals(nodeB.locator().id(), foundEdgeAb.target().locator().id());
        assertEquals(1, foundEdgeAb.target().locator().version()); // Should still point to v1

        assertEquals(nodeB.locator().id(), foundEdgeBc.source().locator().id());
        assertEquals(nodeC.locator().id(), foundEdgeBc.target().locator().id());
    }
}
