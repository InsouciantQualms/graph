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
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.jgrapht.JGraphtComponentOperations;
import dev.iq.graph.model.jgrapht.JGraphtEdgeOperations;
import dev.iq.graph.model.jgrapht.JGraphtNodeOperations;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ComponentSpace.
 */
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
        graph = new DirectedMultigraph<>(null, null, false);
        edgeOps = new JGraphtEdgeOperations(graph);
        nodeOps = new JGraphtNodeOperations(graph, edgeOps);
        componentOps = new JGraphtComponentOperations(graph, nodeOps, edgeOps);
    }

    @Test
    final void testAddComponent() {
        // Create component
        final var componentData = new TestData("Component1");
        final var component = componentOps.add(componentType, componentData, timestamp);

        assertNotNull(component);
        assertEquals(componentType, component.type());
        assertEquals(componentData, component.data());
        assertEquals(timestamp, component.created());
        assertTrue(component.expired().isEmpty());
        assertEquals(1, component.locator().version());
    }

    @Test
    final void testAddComponentAndReferenceFromNode() {
        // Create component
        final var component = componentOps.add(componentType, new TestData("TestComponent"), timestamp);

        // Create node that references the component
        final Set<Locator> components = new HashSet<>();
        components.add(component.locator());

        final var node = nodeOps.add(defaultType, new TestData("Node1"), components, timestamp);

        assertNotNull(node);
        assertEquals(1, node.components().size());
        assertTrue(node.components().contains(component.locator()));
    }

    @Test
    final void testFindActiveComponent() {
        // Create component
        final var component = componentOps.add(componentType, new TestData("Component1"), timestamp);

        // Get all active components and verify ours is present
        final var activeComponents = componentOps.allActive();
        assertTrue(activeComponents.stream()
                .anyMatch(c -> c.locator().id().equals(component.locator().id())));
    }

    @Test
    final void testExpireComponent() {
        // Create component
        final var component = componentOps.add(componentType, new TestData("Component1"), timestamp);

        // Expire component
        final var expiredComponent = componentOps.expire(component.locator().id(), timestamp.plusSeconds(1));

        assertNotNull(expiredComponent);
        assertTrue(expiredComponent.expired().isPresent());
        assertEquals(timestamp.plusSeconds(1), expiredComponent.expired().get());

        // Verify component is no longer in active list
        final var activeComponents = componentOps.allActive();
        assertTrue(activeComponents.stream()
                .noneMatch(c -> c.locator().id().equals(component.locator().id())));
    }

    @Test
    final void testUpdateComponent() {
        // Create initial component
        final var component = componentOps.add(componentType, new TestData("Component1"), timestamp);

        // Update component data
        final var updatedComponent = componentOps.update(
                component.locator().id(), new TestData("UpdatedComponent"), timestamp.plusSeconds(1));

        assertNotNull(updatedComponent);
        assertEquals(2, updatedComponent.locator().version());
        assertEquals("UpdatedComponent", updatedComponent.data().value());
        assertEquals(componentType, updatedComponent.type());
    }

    @Test
    final void testUpdateComponentType() {
        // Create initial component
        final var originalType = new SimpleType("original");
        final var component = componentOps.add(originalType, new TestData("Component1"), timestamp);

        // Update component with new type
        final var newType = new SimpleType("updated");
        final var updatedComponent = componentOps.update(
                component.locator().id(), newType, new TestData("UpdatedComponent"), timestamp.plusSeconds(1));

        assertNotNull(updatedComponent);
        assertEquals(2, updatedComponent.locator().version());
        assertEquals("updated", updatedComponent.type().code());
        assertEquals("UpdatedComponent", updatedComponent.data().value());
    }

    @Test
    final void testComponentVersioning() {
        // Create initial component
        final var component1 = componentOps.add(componentType, new TestData("Component1"), timestamp);

        // Update component
        final var component2 =
                componentOps.update(component1.locator().id(), new TestData("Component2"), timestamp.plusSeconds(1));

        // Verify versions
        assertEquals(1, component1.locator().version());
        assertEquals(2, component2.locator().version());
        assertEquals(component1.locator().id(), component2.locator().id());
        assertEquals("Component2", component2.data().value());
    }

    @Test
    final void testAllActiveComponents() {
        // Create multiple components
        final var component1 = componentOps.add(componentType, new TestData("Component1"), timestamp);
        final var component2 = componentOps.add(componentType, new TestData("Component2"), timestamp);

        // Get all active components
        final var activeComponents = componentOps.allActive();

        assertTrue(activeComponents.size() >= 2);
        assertTrue(activeComponents.stream()
                .anyMatch(c -> c.locator().id().equals(component1.locator().id())));
        assertTrue(activeComponents.stream()
                .anyMatch(c -> c.locator().id().equals(component2.locator().id())));
    }

    @Test
    final void testComponentReferentialIntegrity() {
        // Create component
        final var component = componentOps.add(componentType, new TestData("SharedComponent"), timestamp);

        // Create elements that reference the component
        final Set<Locator> components = new HashSet<>();
        components.add(component.locator());

        final var node1 = nodeOps.add(defaultType, new TestData("Node1"), components, timestamp.plusSeconds(1));
        final var node2 = nodeOps.add(defaultType, new TestData("Node2"), components, timestamp.plusSeconds(1));

        // Update component (should trigger element updates)
        final var updatedComponent = componentOps.update(
                component.locator().id(), new TestData("UpdatedComponent"), timestamp.plusSeconds(2));

        // Verify elements were updated to reference new component version
        final var updatedNode1 = graph.vertexSet().stream()
                .filter(n -> n.locator().id().equals(node1.locator().id())
                        && n.expired().isEmpty())
                .findFirst()
                .orElseThrow();
        final var updatedNode2 = graph.vertexSet().stream()
                .filter(n -> n.locator().id().equals(node2.locator().id())
                        && n.expired().isEmpty())
                .findFirst()
                .orElseThrow();

        assertEquals(2, updatedNode1.locator().version());
        assertEquals(2, updatedNode2.locator().version());
        assertTrue(updatedNode1.components().contains(updatedComponent.locator()));
        assertTrue(updatedNode2.components().contains(updatedComponent.locator()));
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
