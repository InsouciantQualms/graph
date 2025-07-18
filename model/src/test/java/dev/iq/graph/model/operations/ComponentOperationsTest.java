/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.jgrapht.ComponentOperations;
import dev.iq.graph.model.jgrapht.EdgeOperations;
import dev.iq.graph.model.jgrapht.NodeOperations;
import dev.iq.graph.model.simple.SimpleData;
import java.time.Instant;
import java.util.List;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ComponentOperations.
 */
class ComponentOperationsTest {

    private NodeOperations nodeOps;
    private EdgeOperations edgeOps;
    private ComponentOperations componentOps;
    private final Instant timestamp = Instant.now();

    @BeforeEach
    final void before() {
        final Graph<Node, Edge> graph = new DirectedMultigraph<>(null, null, false);
        edgeOps = new EdgeOperations(graph);
        nodeOps = new NodeOperations(graph, edgeOps);
        componentOps = new ComponentOperations(graph);
    }

    @Test
    final void testAddComponent() {
        // Create nodes
        final var node1 = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(new SimpleData(String.class, "Node2"), timestamp);

        // Create edge
        final var edge = edgeOps.add(node1, node2, new SimpleData(String.class, "Edge1"), timestamp);

        // Create component
        final var elements = List.of(node1, node2, edge);
        final var componentData = new SimpleData(String.class, "Component1");
        final var component = componentOps.add(elements, componentData, timestamp);

        assertNotNull(component);
        assertEquals(3, component.elements().size());
        assertEquals(componentData, component.data());
        assertEquals(timestamp, component.created());
        assertTrue(component.expired().isEmpty());
    }

    @Test
    final void testAddComponentWithSingleNode() {
        // Create single node
        final var node = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);

        // Create component with single node
        final List<Element> elements = List.of(node);
        final var componentData = new SimpleData(String.class, "SingleNodeComponent");
        final var component = componentOps.add(elements, componentData, timestamp);

        assertNotNull(component);
        assertEquals(1, component.elements().size());
        assertEquals(node, component.elements().getFirst());
    }

    @Test
    final void testAddComponentValidatesNoEmptyElements() {
        assertThrows(
                IllegalArgumentException.class,
                () -> componentOps.add(List.of(), new SimpleData(String.class, "Empty"), timestamp));
    }

    @Test
    final void testAddComponentValidatesAtLeastOneNode() {
        // Create nodes and edge
        final var node1 = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(new SimpleData(String.class, "Node2"), timestamp);
        final var edge = edgeOps.add(node1, node2, new SimpleData(String.class, "Edge1"), timestamp);

        // Try to create component with only edge (no nodes)
        assertThrows(
                IllegalArgumentException.class,
                () -> componentOps.add(List.of(edge), new SimpleData(String.class, "EdgeOnly"), timestamp));
    }

    @Test
    final void testAddComponentValidatesConnectivity() {
        // Create disconnected nodes
        final var node1 = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(new SimpleData(String.class, "Node2"), timestamp);
        final var node3 = nodeOps.add(new SimpleData(String.class, "Node3"), timestamp);

        // Create edge between node1 and node2 only
        final var edge = edgeOps.add(node1, node2, new SimpleData(String.class, "Edge1"), timestamp);

        // Try to create component with disconnected node3
        assertThrows(
                IllegalArgumentException.class,
                () -> componentOps.add(
                        List.of(node1, node2, node3, edge), new SimpleData(String.class, "Disconnected"), timestamp));
    }

    @Test
    final void testAddComponentValidatesNoCycles() {
        // Create nodes
        final var node1 = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(new SimpleData(String.class, "Node2"), timestamp);
        final var node3 = nodeOps.add(new SimpleData(String.class, "Node3"), timestamp);

        // Create edges forming a cycle
        final var edge1 = edgeOps.add(node1, node2, new SimpleData(String.class, "Edge1"), timestamp);
        final var edge2 = edgeOps.add(node2, node3, new SimpleData(String.class, "Edge2"), timestamp);
        final var edge3 = edgeOps.add(node3, node1, new SimpleData(String.class, "Edge3"), timestamp);

        // Try to create component with cycle
        assertThrows(
                IllegalArgumentException.class,
                () -> componentOps.add(
                        List.of(node1, node2, node3, edge1, edge2, edge3),
                        new SimpleData(String.class, "Cyclic"),
                        timestamp));
    }

    @Test
    final void testUpdateComponent() {
        // Create initial component
        final var node1 = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(new SimpleData(String.class, "Node2"), timestamp);
        final var edge = edgeOps.add(node1, node2, new SimpleData(String.class, "Edge1"), timestamp);

        final var component =
                componentOps.add(List.of(node1, node2, edge), new SimpleData(String.class, "Component1"), timestamp);

        // Add new node and edge
        final var node3 = nodeOps.add(new SimpleData(String.class, "Node3"), timestamp.plusSeconds(1));
        final var edge2 = edgeOps.add(node2, node3, new SimpleData(String.class, "Edge2"), timestamp.plusSeconds(1));

        // Update component
        final var updatedComponent = componentOps.update(
                component.locator().id(),
                List.of(node1, node2, node3, edge, edge2),
                new SimpleData(String.class, "UpdatedComponent"),
                timestamp.plusSeconds(2));

        assertNotNull(updatedComponent);
        assertEquals(5, updatedComponent.elements().size());
        assertEquals(2, updatedComponent.locator().version());
        assertEquals("UpdatedComponent", updatedComponent.data().value());
    }

    @Test
    final void testFindActive() {
        // Create component
        final var node = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var component = componentOps.add(List.of(node), new SimpleData(String.class, "Component1"), timestamp);

        // Find active component
        final var found = componentOps.findActive(component.locator().id());
        assertTrue(found.isPresent());
        assertEquals(component.locator().id(), found.get().locator().id());
    }

    @Test
    final void testExpireComponent() {
        // Create component
        final var node = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var component = componentOps.add(List.of(node), new SimpleData(String.class, "Component1"), timestamp);

        // Expire component
        final var expiredComponent = componentOps.expire(component.locator().id(), timestamp.plusSeconds(1));

        assertNotNull(expiredComponent);
        assertTrue(expiredComponent.expired().isPresent());
        assertEquals(timestamp.plusSeconds(1), expiredComponent.expired().get());

        // Verify no active component exists
        final var found = componentOps.findActive(component.locator().id());
        assertTrue(found.isEmpty());
    }

    @Test
    final void testFindAllVersions() {
        // Create initial component
        final var node1 = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var component1 = componentOps.add(List.of(node1), new SimpleData(String.class, "Component1"), timestamp);

        // Update component - add connected node and edge
        final var node2 = nodeOps.add(new SimpleData(String.class, "Node2"), timestamp.plusSeconds(1));
        final var edge = edgeOps.add(node1, node2, new SimpleData(String.class, "Edge1"), timestamp.plusSeconds(1));
        final var component2 = componentOps.update(
                component1.locator().id(),
                List.of(node1, node2, edge),
                new SimpleData(String.class, "Component2"),
                timestamp.plusSeconds(2));

        // Find all versions
        final var versions = componentOps.findAllVersions(component1.locator().id());

        assertEquals(2, versions.size());
        assertEquals(1, versions.get(0).locator().version());
        assertEquals(2, versions.get(1).locator().version());
    }

    @Test
    final void testFindComponentsContaining() {
        // Create nodes
        final var node1 = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(new SimpleData(String.class, "Node2"), timestamp);
        final var node3 = nodeOps.add(new SimpleData(String.class, "Node3"), timestamp);

        // Create edges to connect nodes
        final var edge1 = edgeOps.add(node1, node2, new SimpleData(String.class, "Edge1"), timestamp);
        final var edge2 = edgeOps.add(node2, node3, new SimpleData(String.class, "Edge2"), timestamp);

        // Create components
        final var component1 =
                componentOps.add(List.of(node1, node2, edge1), new SimpleData(String.class, "Component1"), timestamp);
        final var component2 =
                componentOps.add(List.of(node2, node3, edge2), new SimpleData(String.class, "Component2"), timestamp);

        // Find components containing node2
        final var components = componentOps.findComponentsContaining(node2);

        assertEquals(2, components.size());
        assertTrue(components.stream()
                .anyMatch(c -> c.locator().id().equals(component1.locator().id())));
        assertTrue(components.stream()
                .anyMatch(c -> c.locator().id().equals(component2.locator().id())));
    }
}
