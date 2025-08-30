/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api.impl;

import static org.junit.jupiter.api.Assertions.*;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.api.ComponentService;
import dev.iq.graph.api.EdgeService;
import dev.iq.graph.api.GraphService;
import dev.iq.graph.api.NodeService;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Path;
import dev.iq.graph.model.operations.ComponentOperations;
import dev.iq.graph.model.operations.EdgeOperations;
import dev.iq.graph.model.operations.NodeOperations;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.persistence.GraphRepository;
import dev.iq.graph.persistence.SessionFactory;
import dev.iq.graph.persistence.memory.InMemoryGraphRepository;
import dev.iq.graph.persistence.memory.InMemorySessionFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("API Services Integration Tests")
class ApiIntegrationTest {

    private GraphRepository graphRepository;
    private SessionFactory sessionFactory;
    private NodeOperations nodeOperations;
    private EdgeOperations edgeOperations;
    private ComponentOperations componentOperations;

    private NodeService nodeService;
    private EdgeService edgeService;
    private ComponentService componentService;
    private GraphService graphService;

    @BeforeEach
    void setUp() {
        // Create in-memory repositories for testing
        graphRepository = new InMemoryGraphRepository();
        sessionFactory = new InMemorySessionFactory();

        // Create operations
        nodeOperations = new NodeOperations(graphRepository.nodes());
        edgeOperations = new EdgeOperations(graphRepository.edges(), graphRepository.nodes());
        componentOperations = new ComponentOperations(graphRepository.components());

        // Create services
        nodeService = new DefaultNodeService(graphRepository, sessionFactory, nodeOperations);
        edgeService = new DefaultEdgeService(graphRepository, sessionFactory, edgeOperations);
        componentService = new DefaultComponentService(graphRepository, sessionFactory, componentOperations);
        graphService = new DefaultGraphService(graphRepository);
    }

    @Test
    @DisplayName("Create nodes and verify they can be retrieved")
    void testNodeCreationAndRetrieval() {
        // Create a node
        final Data nodeData = new SimpleData(Map.of("name", "Node1", "type", "TestNode"));
        final Node createdNode = nodeService.add(nodeData);

        assertNotNull(createdNode);
        assertNotNull(createdNode.locator());
        assertEquals(nodeData, createdNode.data());

        // Retrieve the node
        final Optional<Node> retrievedNode = nodeService.findActive(createdNode.locator().id());
        assertTrue(retrievedNode.isPresent());
        assertEquals(createdNode.locator().id(), retrievedNode.get().locator().id());

        // Verify it appears in active list
        final List<NanoId> activeNodes = nodeService.allActive();
        assertTrue(activeNodes.contains(createdNode.locator().id()));
    }

    @Test
    @DisplayName("Update node and verify changes")
    void testNodeUpdate() {
        // Create a node
        final Data initialData = new SimpleData(Map.of("name", "InitialName"));
        final Node createdNode = nodeService.add(initialData);

        // Update the node
        final Data updatedData = new SimpleData(Map.of("name", "UpdatedName", "version", "2"));
        final Node updatedNode = nodeService.update(createdNode.locator().id(), updatedData);

        assertNotNull(updatedNode);
        assertEquals(updatedData, updatedNode.data());

        // Verify the update
        final Optional<Node> retrievedNode = nodeService.findActive(createdNode.locator().id());
        assertTrue(retrievedNode.isPresent());
        assertEquals(updatedData, retrievedNode.get().data());
    }

    @Test
    @DisplayName("Create edges between nodes and verify connections")
    void testEdgeCreationBetweenNodes() {
        // Create two nodes
        final Node node1 = nodeService.add(new SimpleData(Map.of("name", "Node1")));
        final Node node2 = nodeService.add(new SimpleData(Map.of("name", "Node2")));

        // Create an edge between them
        final Data edgeData = new SimpleData(Map.of("weight", 1.5, "type", "connects"));
        final Edge createdEdge = edgeService.addEdge(node1, node2, edgeData);

        assertNotNull(createdEdge);
        assertEquals(node1.locator().id(), createdEdge.source());
        assertEquals(node2.locator().id(), createdEdge.target());
        assertEquals(edgeData, createdEdge.data());

        // Verify the edge can be retrieved
        final Optional<Edge> retrievedEdge = edgeService.findActive(createdEdge.locator().id());
        assertTrue(retrievedEdge.isPresent());

        // Verify edges from node1
        final List<Edge> edgesFromNode1 = edgeService.getEdgesFrom(node1.locator().id());
        assertFalse(edgesFromNode1.isEmpty());
        assertTrue(edgesFromNode1.stream()
                .anyMatch(e -> e.target().equals(node2.locator().id())));

        // Verify edges to node2
        final List<Edge> edgesToNode2 = edgeService.getEdgesTo(node2.locator().id());
        assertFalse(edgesToNode2.isEmpty());
        assertTrue(edgesToNode2.stream()
                .anyMatch(e -> e.source().equals(node1.locator().id())));
    }

    @Test
    @DisplayName("Test graph path operations")
    void testGraphPathOperations() {
        // Create a chain of nodes: Node1 -> Node2 -> Node3
        final Node node1 = nodeService.add(new SimpleData(Map.of("name", "Node1")));
        final Node node2 = nodeService.add(new SimpleData(Map.of("name", "Node2")));
        final Node node3 = nodeService.add(new SimpleData(Map.of("name", "Node3")));

        // Create edges
        edgeService.addEdge(node1, node2, new SimpleData(Map.of("weight", 1.0)));
        edgeService.addEdge(node2, node3, new SimpleData(Map.of("weight", 2.0)));

        // Test path existence
        assertTrue(graphService.hasPath(node1.locator().id(), node3.locator().id()));
        assertTrue(graphService.hasPath(node1.locator().id(), node2.locator().id()));
        assertTrue(graphService.hasPath(node2.locator().id(), node3.locator().id()));

        // Test shortest path
        final Path shortestPath = graphService.getShortestPath(node1.locator().id(), node3.locator().id());
        assertNotNull(shortestPath);

        // Test connected paths
        final List<Path> connectedPaths = graphService.getActiveConnected();
        assertNotNull(connectedPaths);
        assertFalse(connectedPaths.isEmpty());
    }

    @Test
    @DisplayName("Test node neighbors")
    void testNodeNeighbors() {
        // Create a star topology: Center node connected to 3 other nodes
        final Node centerNode = nodeService.add(new SimpleData(Map.of("name", "Center")));
        final Node node1 = nodeService.add(new SimpleData(Map.of("name", "Node1")));
        final Node node2 = nodeService.add(new SimpleData(Map.of("name", "Node2")));
        final Node node3 = nodeService.add(new SimpleData(Map.of("name", "Node3")));

        // Create edges from center to all other nodes
        edgeService.addEdge(centerNode, node1, new SimpleData(Collections.emptyMap()));
        edgeService.addEdge(centerNode, node2, new SimpleData(Collections.emptyMap()));
        edgeService.addEdge(centerNode, node3, new SimpleData(Collections.emptyMap()));

        // Get neighbors of center node
        final List<Node> neighbors = nodeService.getNeighbors(centerNode.locator().id());
        assertNotNull(neighbors);
        assertEquals(3, neighbors.size());

        // Verify all expected neighbors are present
        assertTrue(neighbors.stream().anyMatch(n -> n.locator().id().equals(node1.locator().id())));
        assertTrue(neighbors.stream().anyMatch(n -> n.locator().id().equals(node2.locator().id())));
        assertTrue(neighbors.stream().anyMatch(n -> n.locator().id().equals(node3.locator().id())));
    }

    @Test
    @DisplayName("Test component operations")
    void testComponentOperations() {
        // Create a component
        final Data componentData = new SimpleData(Map.of("name", "Component1", "version", "1.0"));
        final Component createdComponent = componentService.add(Collections.emptyList(), componentData);

        assertNotNull(createdComponent);
        assertNotNull(createdComponent.locator());
        assertEquals(componentData, createdComponent.data());

        // Retrieve the component
        final Optional<Component> retrievedComponent = componentService.findActive(createdComponent.locator().id());
        assertTrue(retrievedComponent.isPresent());
        assertEquals(createdComponent.locator().id(), retrievedComponent.get().locator().id());

        // Update the component
        final Data updatedComponentData = new SimpleData(Map.of("name", "Component1", "version", "2.0"));
        final Component updatedComponent = componentService.update(
                createdComponent.locator().id(),
                Collections.emptyList(),
                updatedComponentData);

        assertNotNull(updatedComponent);
        assertEquals(updatedComponentData, updatedComponent.data());

        // Verify it appears in active list
        final List<NanoId> activeComponents = componentService.allActive();
        assertTrue(activeComponents.contains(createdComponent.locator().id()));
    }

    @Test
    @DisplayName("Test node expiration")
    void testNodeExpiration() {
        // Create a node
        final Node node = nodeService.add(new SimpleData(Map.of("name", "ExpireMe")));
        final NanoId nodeId = node.locator().id();

        // Verify node is active
        assertTrue(nodeService.findActive(nodeId).isPresent());

        // Expire the node
        final Optional<Node> expiredNode = nodeService.expire(nodeId);
        assertTrue(expiredNode.isPresent());

        // Verify node is no longer active
        assertFalse(nodeService.findActive(nodeId).isPresent());

        // But should still exist in all nodes
        assertTrue(nodeService.all().contains(nodeId));
    }

    @Test
    @DisplayName("Test edge expiration")
    void testEdgeExpiration() {
        // Create nodes and edge
        final Node node1 = nodeService.add(new SimpleData(Map.of("name", "Node1")));
        final Node node2 = nodeService.add(new SimpleData(Map.of("name", "Node2")));
        final Edge edge = edgeService.addEdge(node1, node2, new SimpleData(Collections.emptyMap()));
        final NanoId edgeId = edge.locator().id();

        // Verify edge is active
        assertTrue(edgeService.findActive(edgeId).isPresent());

        // Expire the edge
        final Optional<Edge> expiredEdge = edgeService.expire(edgeId);
        assertTrue(expiredEdge.isPresent());

        // Verify edge is no longer active
        assertFalse(edgeService.findActive(edgeId).isPresent());

        // But should still exist in all edges
        assertTrue(edgeService.all().contains(edgeId));
    }

    @Test
    @DisplayName("Test node deletion")
    void testNodeDeletion() {
        // Create a node
        final Node node = nodeService.add(new SimpleData(Map.of("name", "DeleteMe")));
        final NanoId nodeId = node.locator().id();

        // Verify node exists
        assertTrue(nodeService.findActive(nodeId).isPresent());

        // Delete the node
        final boolean deleted = nodeService.delete(nodeId);
        assertTrue(deleted);

        // Verify node no longer exists
        assertFalse(nodeService.findActive(nodeId).isPresent());
        assertFalse(nodeService.all().contains(nodeId));
    }

    @Test
    @DisplayName("Test finding node by locator")
    void testFindNodeByLocator() {
        // Create a node
        final Node node = nodeService.add(new SimpleData(Map.of("name", "FindMe")));
        final Locator locator = node.locator();

        // Find by locator
        final Node foundNode = nodeService.find(locator);
        assertNotNull(foundNode);
        assertEquals(locator, foundNode.locator());
    }

    @Test
    @DisplayName("Test exception when path doesn't exist")
    void testNoPathException() {
        // Create two disconnected nodes
        final Node node1 = nodeService.add(new SimpleData(Map.of("name", "Node1")));
        final Node node2 = nodeService.add(new SimpleData(Map.of("name", "Node2")));

        // Verify no path exists
        assertFalse(graphService.hasPath(node1.locator().id(), node2.locator().id()));

        // Getting shortest path should throw exception
        assertThrows(IllegalStateException.class, () ->
                graphService.getShortestPath(node1.locator().id(), node2.locator().id()));
    }

    @Test
    @DisplayName("Test finding non-existent node throws exception")
    void testFindNonExistentNodeThrowsException() {
        final Locator nonExistentLocator = Locator.generate();

        assertThrows(IllegalArgumentException.class, () ->
                nodeService.find(nonExistentLocator));
    }

    @Test
    @DisplayName("Test updating non-existent node throws exception")
    void testUpdateNonExistentNodeThrowsException() {
        final NanoId nonExistentId = Locator.generate().id();
        final Data data = new SimpleData(Map.of("test", "data"));

        assertThrows(RuntimeException.class, () ->
                nodeService.update(nonExistentId, data));
    }
}