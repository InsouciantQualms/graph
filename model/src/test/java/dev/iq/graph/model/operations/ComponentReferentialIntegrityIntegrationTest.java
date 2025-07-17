/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.jgrapht.ComponentOperations;
import dev.iq.graph.model.jgrapht.EdgeOperations;
import dev.iq.graph.model.jgrapht.NodeOperations;
import dev.iq.graph.model.simple.SimpleData;

/**
 * Integration tests for ComponentOperations referential integrity.
 */
class ComponentReferentialIntegrityIntegrationTest {

    private NodeOperations nodeOps;
    private EdgeOperations edgeOps;
    private ComponentOperations componentOps;
    private final Instant timestamp = Instant.now();

    @BeforeEach
    final void setUp() {

        final Graph<Node, Edge> graph = new DirectedMultigraph<>(null, null, false);
        edgeOps = new EdgeOperations(graph);
        nodeOps = new NodeOperations(graph, edgeOps);
        componentOps = new ComponentOperations(graph);
    }

    @Test
    final void testComponentVersioningMaintainsReferentialIntegrity() {

        // Create initial component
        final var node1 = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(new SimpleData(String.class, "Node2"), timestamp);
        final var edge = edgeOps.add(node1, node2, new SimpleData(String.class, "Edge1"), timestamp);
        final var component =
                componentOps.add(List.of(node1, node2, edge), new SimpleData(String.class, "Component1"), timestamp);

        // Create new versions of nodes
        final var node1v2 =
                nodeOps.update(node1.locator().id(), new SimpleData(String.class, "Node1v2"), timestamp.plusSeconds(1));
        final var node2v2 =
                nodeOps.update(node2.locator().id(), new SimpleData(String.class, "Node2v2"), timestamp.plusSeconds(2));

        // Get current edge (should have been recreated with new node versions)
        final var currentEdges = edgeOps.allActive();
        assertEquals(1, currentEdges.size());
        final var currentEdge = currentEdges.getFirst();

        // Update component with new versions
        final var componentV2 = componentOps.update(
                component.locator().id(),
                List.of(node1v2, node2v2, currentEdge),
                new SimpleData(String.class, "Component1v2"),
                timestamp.plusSeconds(3));

        // Verify referential integrity
        assertEquals(2, componentV2.locator().version());
        assertEquals(3, componentV2.elements().size());

        // Verify the elements in the component are the latest versions
        assertTrue(componentV2.elements().stream().anyMatch(e -> e.equals(node1v2)));
        assertTrue(componentV2.elements().stream().anyMatch(e -> e.equals(node2v2)));
        assertTrue(componentV2.elements().stream().anyMatch(e -> e.equals(currentEdge)));
    }

    @Test
    final void testComponentHistoryPreservesOldVersions() {

        // Create component and update it multiple times
        final var node1 = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var component = componentOps.add(List.of(node1), new SimpleData(String.class, "Component1"), timestamp);

        // Add a second node and update component
        final var node2 = nodeOps.add(new SimpleData(String.class, "Node2"), timestamp.plusSeconds(1));
        final var edge1 = edgeOps.add(node1, node2, new SimpleData(String.class, "Edge1"), timestamp.plusSeconds(1));
        final var componentV2 = componentOps.update(
                component.locator().id(),
                List.of(node1, node2, edge1),
                new SimpleData(String.class, "Component1v2"),
                timestamp.plusSeconds(2));

        // Add a third node and update component again
        final var node3 = nodeOps.add(new SimpleData(String.class, "Node3"), timestamp.plusSeconds(3));
        final var edge2 = edgeOps.add(node2, node3, new SimpleData(String.class, "Edge2"), timestamp.plusSeconds(3));
        final var componentV3 = componentOps.update(
                component.locator().id(),
                List.of(node1, node2, node3, edge1, edge2),
                new SimpleData(String.class, "Component1v3"),
                timestamp.plusSeconds(4));

        // Verify all versions exist
        final var allVersions = componentOps.findAllVersions(component.locator().id());
        assertEquals(3, allVersions.size());

        // Verify version progression
        assertEquals(1, allVersions.get(0).locator().version());
        assertEquals(2, allVersions.get(1).locator().version());
        assertEquals(3, allVersions.get(2).locator().version());

        // Verify element counts
        assertEquals(1, allVersions.get(0).elements().size());
        assertEquals(3, allVersions.get(1).elements().size());
        assertEquals(5, allVersions.get(2).elements().size());
    }

    @Test
    final void testComponentAtSpecificTimestamp() {

        // Create component with initial elements
        final var node1 = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(new SimpleData(String.class, "Node2"), timestamp.plusSeconds(1));
        final var edge = edgeOps.add(node1, node2, new SimpleData(String.class, "Edge1"), timestamp.plusSeconds(2));
        final var component = componentOps.add(
                List.of(node1, node2, edge), new SimpleData(String.class, "Component1"), timestamp.plusSeconds(3));

        // Update component
        final var node3 = nodeOps.add(new SimpleData(String.class, "Node3"), timestamp.plusSeconds(4));
        final var edge2 = edgeOps.add(node2, node3, new SimpleData(String.class, "Edge2"), timestamp.plusSeconds(5));
        componentOps.update(
                component.locator().id(),
                List.of(node1, node2, node3, edge, edge2),
                new SimpleData(String.class, "Component1v2"),
                timestamp.plusSeconds(6));

        // Query component at different timestamps
        final var componentAtT3 = componentOps.findAt(component.locator().id(), timestamp.plusSeconds(3));
        final var componentAtT5 = componentOps.findAt(component.locator().id(), timestamp.plusSeconds(5));
        final var componentAtT7 = componentOps.findAt(component.locator().id(), timestamp.plusSeconds(7));

        // Verify versions at different times
        assertTrue(componentAtT3.isPresent());
        assertEquals(1, componentAtT3.get().locator().version());
        assertEquals(3, componentAtT3.get().elements().size());

        assertTrue(componentAtT5.isPresent());
        assertEquals(1, componentAtT5.get().locator().version());

        assertTrue(componentAtT7.isPresent());
        assertEquals(2, componentAtT7.get().locator().version());
        assertEquals(5, componentAtT7.get().elements().size());
    }

    @Test
    final void testExpiredComponentVersions() {

        // Create and expire component versions
        final var node1 = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var component = componentOps.add(List.of(node1), new SimpleData(String.class, "Component1"), timestamp);

        // Update component (expires v1)
        final var node2 = nodeOps.add(new SimpleData(String.class, "Node2"), timestamp.plusSeconds(1));
        final var edge = edgeOps.add(node1, node2, new SimpleData(String.class, "Edge1"), timestamp.plusSeconds(1));
        componentOps.update(
                component.locator().id(),
                List.of(node1, node2, edge),
                new SimpleData(String.class, "Component1v2"),
                timestamp.plusSeconds(2));

        // Expire the component entirely
        componentOps.expire(component.locator().id(), timestamp.plusSeconds(3));

        // Verify no active component
        final var activeComponent = componentOps.findActive(component.locator().id());
        assertTrue(activeComponent.isEmpty());

        // Verify all versions still exist in history
        final var allVersions = componentOps.findAllVersions(component.locator().id());
        assertEquals(2, allVersions.size());
        assertTrue(allVersions.get(0).expired().isPresent());
        assertTrue(allVersions.get(1).expired().isPresent());
    }

    @Test
    final void testComponentIntegrityWithCircularReferences() {

        // Create nodes
        final var node1 = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(new SimpleData(String.class, "Node2"), timestamp);

        // Create edges in both directions (but not a cycle within component)
        final var edge1 = edgeOps.add(node1, node2, new SimpleData(String.class, "Edge1->2"), timestamp);
        final var edge2 = edgeOps.add(node2, node1, new SimpleData(String.class, "Edge2->1"), timestamp);

        // Create component with only forward edge (no cycle)
        final var component1 = componentOps.add(
                List.of(node1, node2, edge1), new SimpleData(String.class, "ForwardComponent"), timestamp);

        // Create component with only backward edge (no cycle)
        final var component2 = componentOps.add(
                List.of(node1, node2, edge2), new SimpleData(String.class, "BackwardComponent"), timestamp);

        // Verify both components exist
        assertTrue(componentOps.findActive(component1.locator().id()).isPresent());
        assertTrue(componentOps.findActive(component2.locator().id()).isPresent());

        // Try to create component with cycle (should fail)
        assertThrows(
                IllegalArgumentException.class,
                () -> componentOps.add(
                        List.of(node1, node2, edge1, edge2),
                        new SimpleData(String.class, "CyclicComponent"),
                        timestamp));
    }

    @Test
    final void testComponentMembershipConsistency() {

        // Create complex graph
        final var node1 = nodeOps.add(new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(new SimpleData(String.class, "Node2"), timestamp);
        final var node3 = nodeOps.add(new SimpleData(String.class, "Node3"), timestamp);
        final var node4 = nodeOps.add(new SimpleData(String.class, "Node4"), timestamp);

        final var edge1 = edgeOps.add(node1, node2, new SimpleData(String.class, "Edge1"), timestamp);
        final var edge2 = edgeOps.add(node2, node3, new SimpleData(String.class, "Edge2"), timestamp);
        final var edge3 = edgeOps.add(node3, node4, new SimpleData(String.class, "Edge3"), timestamp);

        // Create overlapping components
        final var component1 =
                componentOps.add(List.of(node1, node2, edge1), new SimpleData(String.class, "Component1"), timestamp);
        final var component2 =
                componentOps.add(List.of(node2, node3, edge2), new SimpleData(String.class, "Component2"), timestamp);
        final var component3 =
                componentOps.add(List.of(node3, node4, edge3), new SimpleData(String.class, "Component3"), timestamp);

        // Verify membership consistency
        assertEquals(1, componentOps.findComponentsContaining(node1).size());
        assertEquals(2, componentOps.findComponentsContaining(node2).size());
        assertEquals(2, componentOps.findComponentsContaining(node3).size());
        assertEquals(1, componentOps.findComponentsContaining(node4).size());

        // Update middle component
        componentOps.update(
                component2.locator().id(),
                List.of(node2, node3, node4, edge2, edge3),
                new SimpleData(String.class, "Component2v2"),
                timestamp.plusSeconds(1));

        // Verify updated membership
        assertEquals(1, componentOps.findComponentsContaining(node1).size());
        assertEquals(2, componentOps.findComponentsContaining(node2).size());
        assertEquals(2, componentOps.findComponentsContaining(node3).size());
        assertEquals(2, componentOps.findComponentsContaining(node4).size());
    }
}
