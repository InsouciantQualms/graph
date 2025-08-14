/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.common.version.Locator;
import dev.iq.common.version.Uid;
import dev.iq.common.version.UidFactory;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.jgrapht.JGraphtComponentOperations;
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
 * Unit tests for ComponentOperations.
 */
@DisplayName("Component Operations Tests")
class ComponentOperationsTest {

    private JGraphtNodeOperations nodeOps;
    private JGraphtEdgeOperations edgeOps;
    private JGraphtComponentOperations componentOps;
    private Graph<Node, Edge> graph;
    private final Type defaultType = new SimpleType("test");
    private final Type componentType = new SimpleType("component");
    private final Instant timestamp = Instant.now();

    @BeforeEach
    final void before() {
        graph = new DirectedMultigraph<>(null, null, true); // Allow self-loops
        edgeOps = new JGraphtEdgeOperations(graph);
        nodeOps = new JGraphtNodeOperations(graph, edgeOps);
        componentOps = new JGraphtComponentOperations(graph, nodeOps, edgeOps);
    }

    @Test
    @DisplayName("Add component")
    final void testAddComponent() {
        // Create component
        final var componentData = new SimpleData(String.class, "Component1");
        final var component = componentOps.add(componentType, componentData, timestamp);

        assertNotNull(component);
        assertEquals(componentType, component.type());
        assertEquals(componentData, component.data());
        assertEquals(timestamp, component.created());
        assertTrue(component.expired().isEmpty());
        assertEquals(1, component.locator().version());
    }

    @Test
    @DisplayName("Update component")
    final void testUpdateComponent() {
        // Create component
        final var originalData = new SimpleData(String.class, "Original");
        final var component = componentOps.add(componentType, originalData, timestamp);

        // Update component
        final var updateTime = timestamp.plusSeconds(1);
        final var updatedData = new SimpleData(String.class, "Updated");
        final var updatedComponent =
                componentOps.update(component.locator().id(), componentType, updatedData, updateTime);

        assertNotNull(updatedComponent);
        assertEquals(componentType, updatedComponent.type());
        assertEquals(updatedData, updatedComponent.data());
        assertEquals(updateTime, updatedComponent.created());
        assertEquals(2, updatedComponent.locator().version());
        assertTrue(updatedComponent.expired().isEmpty());

        // Verify original is expired
        final var expiredComponent = componentOps.findActive(component.locator().id());
        assertTrue(expiredComponent.isPresent());
        assertEquals(2, expiredComponent.get().locator().version());
    }

    @Test
    @DisplayName("Expire component")
    final void testExpireComponent() {
        // Create component
        final var component = componentOps.add(componentType, new SimpleData(String.class, "Component"), timestamp);

        // Expire component
        final var expireTime = timestamp.plusSeconds(1);
        final var expiredComponent = componentOps.expire(component.locator().id(), expireTime);

        assertNotNull(expiredComponent);
        assertTrue(expiredComponent.expired().isPresent());
        assertEquals(expireTime, expiredComponent.expired().get());
        assertEquals(1, expiredComponent.locator().version());
    }

    @Test
    @DisplayName("Component update triggers edge updates")
    final void testComponentUpdateTriggersEdgeUpdates() {
        // Create component
        final var component = componentOps.add(componentType, new SimpleData(String.class, "Component"), timestamp);

        // Create nodes
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final var node1 = nodeOps.add(node1Id, defaultType, new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(node2Id, defaultType, new SimpleData(String.class, "Node2"), timestamp);

        // Create edge with component reference
        final Set<Locator> components = new HashSet<>();
        components.add(component.locator());
        final var edge =
                edgeOps.add(defaultType, node1, node2, new SimpleData(String.class, "Edge"), components, timestamp);

        // Update component
        final var updateTime = timestamp.plusSeconds(1);
        final var updatedComponent = componentOps.update(
                component.locator().id(), componentType, new SimpleData(String.class, "UpdatedComponent"), updateTime);

        // Verify edge was updated to reference new component version
        final var updatedEdge = graph.edgeSet().stream()
                .filter(e -> e.expired().isEmpty())
                .filter(e -> e.locator().id().equals(edge.locator().id()))
                .findFirst()
                .orElseThrow();

        assertTrue(updatedEdge.components().contains(updatedComponent.locator()));
        assertEquals(1, updatedEdge.components().size());
    }

    @Test
    @DisplayName("Component validation - empty edges")
    final void testComponentValidationEmptyEdges() {
        // Create nodes
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final var node1 = nodeOps.add(node1Id, defaultType, new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(node2Id, defaultType, new SimpleData(String.class, "Node2"), timestamp);

        // Create empty edge set
        final Set<Edge> edges = new HashSet<>();

        // Should throw exception for empty component
        try {
            JGraphtComponentOperations.validateComponentElements(new HashSet<>(), graph);
            assertTrue(false, "Should have thrown exception for empty component");
        } catch (IllegalArgumentException e) {
            assertEquals("Component must contain at least one element", e.getMessage());
        }
    }

    @Test
    @DisplayName("Find active component")
    final void testFindActiveComponent() {
        // Create and update component multiple times
        final var component1 = componentOps.add(componentType, new SimpleData(String.class, "V1"), timestamp);
        final var component2 = componentOps.update(
                component1.locator().id(), componentType, new SimpleData(String.class, "V2"), timestamp.plusSeconds(1));
        final var component3 = componentOps.update(
                component1.locator().id(), componentType, new SimpleData(String.class, "V3"), timestamp.plusSeconds(2));

        // Find active should return latest version
        final var active = componentOps.findActive(component1.locator().id());
        assertTrue(active.isPresent());
        assertEquals(3, active.get().locator().version());
        assertEquals("V3", ((SimpleData) active.get().data()).value());
    }

    @Test
    @DisplayName("Get all active components")
    final void testGetAllActiveComponents() {
        // Create multiple components
        final var comp1 = componentOps.add(componentType, new SimpleData(String.class, "Comp1"), timestamp);
        final var comp2 = componentOps.add(componentType, new SimpleData(String.class, "Comp2"), timestamp);
        final var comp3 = componentOps.add(componentType, new SimpleData(String.class, "Comp3"), timestamp);

        // Expire one component
        componentOps.expire(comp2.locator().id(), timestamp.plusSeconds(1));

        // Get all active
        final var activeComponents = componentOps.allActive();
        assertEquals(2, activeComponents.size());
        assertTrue(activeComponents.stream()
                .anyMatch(c -> c.locator().id().equals(comp1.locator().id())));
        assertTrue(activeComponents.stream()
                .anyMatch(c -> c.locator().id().equals(comp3.locator().id())));
    }
}
