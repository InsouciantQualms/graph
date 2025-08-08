/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.common.version.Locator;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.jgrapht.mutable.JGraphtMutableComponentOperations;
import dev.iq.graph.model.jgrapht.mutable.JGraphtMutableEdgeOperations;
import dev.iq.graph.model.jgrapht.mutable.JGraphtMutableNodeOperations;
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
 * Integration tests for ComponentSpace referential integrity.
 * Tests the rules:
 * - Components are pure metadata objects (Type + Data)
 * - Elements reference components via Set&lt;Locator&gt;
 * - When a component updates, all elements referencing it must be updated to reference the new version
 * - All related operations use the same timestamp
 */
@DisplayName("Component Referential Integrity Integration Tests")
class ComponentReferentialIntegrityIntegrationTest {

    private Graph<Node, Edge> graph;
    private JGraphtMutableNodeOperations nodeOps;
    private JGraphtMutableEdgeOperations edgeOps;
    private JGraphtMutableComponentOperations componentOps;
    private final Type defaultType = new SimpleType("test");
    private final Type componentType = new SimpleType("component");
    private final Instant timestamp = Instant.now();

    @BeforeEach
    final void before() {
        graph = new DirectedMultigraph<>(null, null, false);
        edgeOps = new JGraphtMutableEdgeOperations(graph);
        nodeOps = new JGraphtMutableNodeOperations(graph, edgeOps);
        componentOps = new JGraphtMutableComponentOperations(graph, nodeOps, edgeOps);
    }

    @Test
    @DisplayName("Component update triggers element updates")
    final void testComponentUpdateTriggersElementUpdates() {
        // Create component
        final var component = componentOps.add(componentType, new SimpleData(String.class, "Component1"), timestamp);

        // Create elements that reference the component
        final Set<Locator> components = new HashSet<>();
        components.add(component.locator());

        final var node1 =
                nodeOps.add(defaultType, new SimpleData(String.class, "Node1"), components, timestamp.plusSeconds(1));
        final var node2 =
                nodeOps.add(defaultType, new SimpleData(String.class, "Node2"), components, timestamp.plusSeconds(1));
        final var edge = edgeOps.add(
                defaultType, node1, node2, new SimpleData(String.class, "Edge1"), components, timestamp.plusSeconds(1));

        // Update component
        final var timestamp2 = timestamp.plusSeconds(2);
        final var updatedComponent = componentOps.update(
                component.locator().id(), new SimpleData(String.class, "UpdatedComponent"), timestamp2);

        // Verify component has new version
        assertEquals(2, updatedComponent.locator().version());
        assertEquals("UpdatedComponent", updatedComponent.data().value());

        // Verify all elements were updated to reference the new component version
        final var allNodes = graph.vertexSet();
        final var allEdges = graph.edgeSet();

        // Find updated nodes
        final var updatedNode1 = allNodes.stream()
                .filter(n -> n.locator().id().equals(node1.locator().id())
                        && n.expired().isEmpty())
                .findFirst()
                .orElseThrow();
        final var updatedNode2 = allNodes.stream()
                .filter(n -> n.locator().id().equals(node2.locator().id())
                        && n.expired().isEmpty())
                .findFirst()
                .orElseThrow();
        // Find the active edge between the updated nodes
        final var updatedEdge = allEdges.stream()
                .filter(e -> e.expired().isEmpty()
                        && e.source().locator().id().equals(node1.locator().id())
                        && e.target().locator().id().equals(node2.locator().id()))
                .findFirst()
                .orElseThrow();

        // Verify elements have new versions
        assertEquals(2, updatedNode1.locator().version(), "Node1 should be version 2 after component update");
        assertEquals(2, updatedNode2.locator().version(), "Node2 should be version 2 after component update");
        // Edge is recreated when nodes are updated, so it's version 1 with a new ID
        assertEquals(1, updatedEdge.locator().version(), "Edge should be version 1 (recreated during node update)");

        // Verify elements reference the new component version
        assertTrue(updatedNode1.components().contains(updatedComponent.locator()));
        assertTrue(updatedNode2.components().contains(updatedComponent.locator()));
        assertTrue(updatedEdge.components().contains(updatedComponent.locator()));

        // Verify old component locator is not referenced
        assertEquals(
                0,
                updatedNode1.components().stream().filter(l -> l.version() == 1).count());
        assertEquals(
                0,
                updatedNode2.components().stream().filter(l -> l.version() == 1).count());
        assertEquals(
                0,
                updatedEdge.components().stream().filter(l -> l.version() == 1).count());

        // Verify all updates have same timestamp
        assertEquals(timestamp2, updatedComponent.created());
        assertEquals(timestamp2, updatedNode1.created());
        assertEquals(timestamp2, updatedNode2.created());
        assertEquals(timestamp2, updatedEdge.created());
    }

    @Test
    @DisplayName("Component type update triggers element updates")
    final void testComponentTypeUpdateTriggersElementUpdates() {
        // Create component
        final var originalType = new SimpleType("original");
        final var component = componentOps.add(originalType, new SimpleData(String.class, "Component1"), timestamp);

        // Create element that references the component
        final Set<Locator> components = new HashSet<>();
        components.add(component.locator());

        final var node =
                nodeOps.add(defaultType, new SimpleData(String.class, "Node1"), components, timestamp.plusSeconds(1));

        // Update component type
        final var newType = new SimpleType("updated");
        final var timestamp2 = timestamp.plusSeconds(2);
        final var updatedComponent = componentOps.update(
                component.locator().id(), newType, new SimpleData(String.class, "UpdatedComponent"), timestamp2);

        // Verify component has new version and type
        assertEquals(2, updatedComponent.locator().version());
        assertEquals("updated", updatedComponent.type().code());

        // Verify node was updated
        final var updatedNode = graph.vertexSet().stream()
                .filter(n -> n.locator().id().equals(node.locator().id())
                        && n.expired().isEmpty())
                .findFirst()
                .orElseThrow();

        assertEquals(2, updatedNode.locator().version());
        assertTrue(updatedNode.components().contains(updatedComponent.locator()));
    }

    @Test
    @DisplayName("Component expire does not trigger element updates")
    final void testComponentExpireIsolation() {
        // Create component
        final var component = componentOps.add(componentType, new SimpleData(String.class, "Component1"), timestamp);

        // Create elements that reference the component
        final Set<Locator> components = new HashSet<>();
        components.add(component.locator());

        final var node =
                nodeOps.add(defaultType, new SimpleData(String.class, "Node1"), components, timestamp.plusSeconds(1));
        // Create another node to avoid self-loop
        final var node2 = nodeOps.add(
                defaultType, new SimpleData(String.class, "Node2"), new HashSet<>(), timestamp.plusSeconds(1));
        final var edge = edgeOps.add(
                defaultType, node, node2, new SimpleData(String.class, "Edge"), components, timestamp.plusSeconds(1));

        // Expire component
        final var timestamp2 = timestamp.plusSeconds(2);
        final var expiredComponent = componentOps.expire(component.locator().id(), timestamp2);

        // Verify component is expired
        assertTrue(expiredComponent.expired().isPresent());
        assertEquals(timestamp2, expiredComponent.expired().get());

        // Verify elements are NOT updated (they still reference the expired component)
        final var currentNode = graph.vertexSet().stream()
                .filter(n -> n.locator().id().equals(node.locator().id()))
                .findFirst()
                .orElseThrow();
        final var currentEdge = graph.edgeSet().stream()
                .filter(e -> e.locator().id().equals(edge.locator().id()))
                .findFirst()
                .orElseThrow();

        assertEquals(1, currentNode.locator().version(), "Node should not be updated when component expires");
        assertEquals(1, currentEdge.locator().version(), "Edge should not be updated when component expires");
        assertTrue(
                currentNode.components().contains(component.locator()),
                "Node should still reference expired component");
        assertTrue(
                currentEdge.components().contains(component.locator()),
                "Edge should still reference expired component");
    }

    @Test
    @DisplayName("Multiple components on elements with selective updates")
    final void testMultipleComponentsSelectiveUpdate() {
        // Create multiple components
        final var component1 = componentOps.add(componentType, new SimpleData(String.class, "Component1"), timestamp);
        final var component2 = componentOps.add(componentType, new SimpleData(String.class, "Component2"), timestamp);
        final var component3 = componentOps.add(componentType, new SimpleData(String.class, "Component3"), timestamp);

        // Create node with multiple components
        final Set<Locator> nodeComponents = new HashSet<>();
        nodeComponents.add(component1.locator());
        nodeComponents.add(component2.locator());

        final var node = nodeOps.add(
                defaultType, new SimpleData(String.class, "Node1"), nodeComponents, timestamp.plusSeconds(1));

        // Create edge with different components
        final Set<Locator> edgeComponents = new HashSet<>();
        edgeComponents.add(component2.locator());
        edgeComponents.add(component3.locator());

        // Create another node to connect with edge
        final var node3 = nodeOps.add(
                defaultType, new SimpleData(String.class, "Node3"), new HashSet<>(), timestamp.plusSeconds(1));
        final var edge = edgeOps.add(
                defaultType,
                node,
                node3,
                new SimpleData(String.class, "Edge1"),
                edgeComponents,
                timestamp.plusSeconds(1));

        // Update component2 (should affect both node and edge)
        final var timestamp2 = timestamp.plusSeconds(2);
        final var updatedComponent2 = componentOps.update(
                component2.locator().id(), new SimpleData(String.class, "UpdatedComponent2"), timestamp2);

        // Verify both elements were updated
        final var updatedNode = graph.vertexSet().stream()
                .filter(n -> n.locator().id().equals(node.locator().id())
                        && n.expired().isEmpty())
                .findFirst()
                .orElseThrow();
        // Find the active edge between the nodes
        final var updatedEdge = graph.edgeSet().stream()
                .filter(e -> e.expired().isEmpty()
                        && e.source().locator().id().equals(node.locator().id())
                        && e.target().locator().id().equals(node3.locator().id()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, updatedNode.locator().version());
        // Edge is recreated when node is updated, so it's version 1
        assertEquals(1, updatedEdge.locator().version());

        // Verify component references are correct
        assertTrue(updatedNode.components().contains(component1.locator()), "Node should still have component1");
        assertTrue(
                updatedNode.components().contains(updatedComponent2.locator()), "Node should have updated component2");
        assertTrue(
                updatedNode.components().stream().noneMatch(l -> l.equals(component2.locator())),
                "Node should not have old component2");

        assertTrue(
                updatedEdge.components().contains(updatedComponent2.locator()), "Edge should have updated component2");
        assertTrue(updatedEdge.components().contains(component3.locator()), "Edge should still have component3");
        assertTrue(
                updatedEdge.components().stream().noneMatch(l -> l.equals(component2.locator())),
                "Edge should not have old component2");
    }

    @Test
    @DisplayName("Component update cascades through complex graph")
    final void testComponentUpdateCascadeComplexGraph() {
        // Create component
        final var component =
                componentOps.add(componentType, new SimpleData(String.class, "SharedComponent"), timestamp);

        // Create complex graph where many elements reference the component
        final Set<Locator> components = new HashSet<>();
        components.add(component.locator());

        // Create nodes
        final var node1 =
                nodeOps.add(defaultType, new SimpleData(String.class, "Node1"), components, timestamp.plusSeconds(1));
        final var node2 =
                nodeOps.add(defaultType, new SimpleData(String.class, "Node2"), components, timestamp.plusSeconds(1));
        final var node3 = nodeOps.add(
                defaultType,
                new SimpleData(String.class, "Node3"),
                new HashSet<>(),
                timestamp.plusSeconds(1)); // No component
        final var node4 =
                nodeOps.add(defaultType, new SimpleData(String.class, "Node4"), components, timestamp.plusSeconds(1));

        // Create edges
        final var edge12 = edgeOps.add(
                defaultType,
                node1,
                node2,
                new SimpleData(String.class, "Edge12"),
                components,
                timestamp.plusSeconds(1));
        final var edge23 = edgeOps.add(
                defaultType,
                node2,
                node3,
                new SimpleData(String.class, "Edge23"),
                new HashSet<>(),
                timestamp.plusSeconds(1)); // No component
        final var edge34 = edgeOps.add(
                defaultType,
                node3,
                node4,
                new SimpleData(String.class, "Edge34"),
                components,
                timestamp.plusSeconds(1));

        // Update component
        final var timestamp2 = timestamp.plusSeconds(2);
        final var updatedComponent = componentOps.update(
                component.locator().id(), new SimpleData(String.class, "UpdatedSharedComponent"), timestamp2);

        // Count updated elements
        final var updatedNodes = graph.vertexSet().stream()
                .filter(n -> n.locator().version() == 2)
                .count();
        // Count edges that have the updated component (either updated directly or recreated)
        final var edgesWithUpdatedComponent = graph.edgeSet().stream()
                .filter(e -> e.expired().isEmpty() && e.components().contains(updatedComponent.locator()))
                .count();

        assertEquals(3, updatedNodes, "Three nodes should be updated (node1, node2, node4)");
        assertEquals(2, edgesWithUpdatedComponent, "Two edges should have the updated component (edge12, edge34)");

        // Verify node3 and edge23 were not updated (they don't reference the component)
        final var currentNode3 = graph.vertexSet().stream()
                .filter(n -> n.locator().id().equals(node3.locator().id()))
                .findFirst()
                .orElseThrow();
        final var currentEdge23 = graph.edgeSet().stream()
                .filter(e -> e.locator().id().equals(edge23.locator().id()))
                .findFirst()
                .orElseThrow();

        assertEquals(1, currentNode3.locator().version(), "Node3 should not be updated");
        assertEquals(1, currentEdge23.locator().version(), "Edge23 should not be updated");
    }

    @Test
    @DisplayName("Component history preserved through updates")
    final void testComponentHistoryPreservation() {
        // Create and update component multiple times
        final var component1 = componentOps.add(componentType, new SimpleData(String.class, "V1"), timestamp);
        final var component2 = componentOps.update(
                component1.locator().id(), new SimpleData(String.class, "V2"), timestamp.plusSeconds(1));
        final var component3 = componentOps.update(
                component1.locator().id(), new SimpleData(String.class, "V3"), timestamp.plusSeconds(2));

        // Verify all versions exist
        final var allVersions = componentOps.allActive(); // This gets all components, we need to filter
        final var componentVersions = allVersions.stream()
                .filter(c -> c.locator().id().equals(component1.locator().id()))
                .toList();

        // Since allActive only returns active components, we should only see the latest
        assertEquals(1, componentVersions.size());
        assertEquals(3, componentVersions.get(0).locator().version());

        // Verify version progression
        assertEquals(1, component1.locator().version());
        assertEquals(2, component2.locator().version());
        assertEquals(3, component3.locator().version());

        // Verify all share same ID
        assertEquals(component1.locator().id(), component2.locator().id());
        assertEquals(component1.locator().id(), component3.locator().id());
    }

    @Test
    @DisplayName("Timestamp consistency in component-triggered updates")
    final void testTimestampConsistencyInComponentUpdates() {
        // Create component
        final var component = componentOps.add(componentType, new SimpleData(String.class, "Component"), timestamp);

        // Create multiple elements referencing the component
        final Set<Locator> components = new HashSet<>();
        components.add(component.locator());

        final var baseTime = timestamp.plusSeconds(1);
        final var node1 = nodeOps.add(defaultType, new SimpleData(String.class, "Node1"), components, baseTime);
        final var node2 =
                nodeOps.add(defaultType, new SimpleData(String.class, "Node2"), components, baseTime.plusSeconds(1));
        final var edge = edgeOps.add(
                defaultType, node1, node2, new SimpleData(String.class, "Edge"), components, baseTime.plusSeconds(2));

        // Update component
        final var updateTime = timestamp.plusSeconds(10);
        final var updatedComponent =
                componentOps.update(component.locator().id(), new SimpleData(String.class, "Updated"), updateTime);

        // Verify all updates have the exact same timestamp
        final var activeNodes =
                graph.vertexSet().stream().filter(n -> n.expired().isEmpty()).toList();
        final var activeEdges =
                graph.edgeSet().stream().filter(e -> e.expired().isEmpty()).toList();

        // All active elements should have been updated
        assertEquals(2, activeNodes.size());
        assertEquals(1, activeEdges.size());

        // All should have the update timestamp
        assertTrue(activeNodes.stream().allMatch(n -> n.created().equals(updateTime)));
        assertTrue(activeEdges.stream().allMatch(e -> e.created().equals(updateTime)));
        assertEquals(updateTime, updatedComponent.created());

        // Expired versions should also have consistent timestamp
        final var expiredElements =
                graph.vertexSet().stream().filter(n -> n.expired().isPresent()).toList();
        assertTrue(expiredElements.stream().allMatch(n -> n.expired().get().equals(updateTime)));
    }
}
