/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.common.version.Locator;
import dev.iq.common.version.Uid;
import dev.iq.common.version.UidFactory;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.jgrapht.GraphComponentStrategy;
import dev.iq.graph.model.jgrapht.JGraphtEdgeOperations;
import dev.iq.graph.model.jgrapht.JGraphtNodeOperations;
import dev.iq.graph.model.jgrapht.SeparateComponentStrategy;
import dev.iq.graph.model.simple.SimpleComponent;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for ComponentStrategy implementations (SeparateComponentStrategy and GraphComponentStrategy).
 */
@DisplayName("Component Strategy Tests")
class ComponentStrategyTest {

    private Graph<Node, Edge> graph;
    private JGraphtNodeOperations nodeOps;
    private JGraphtEdgeOperations edgeOps;
    private final Type defaultType = new SimpleType("test");
    private final Type componentType = new SimpleType("component");
    private final Instant timestamp = Instant.now();

    @BeforeEach
    final void before() {
        graph = new DirectedMultigraph<>(null, null, true); // Allow self-loops
        edgeOps = new JGraphtEdgeOperations(graph);
        nodeOps = new JGraphtNodeOperations(graph, edgeOps);
    }

    @Test
    @DisplayName("SeparateComponentStrategy - Store and retrieve component")
    final void testSeparateComponentStrategyStore() {
        final var strategy = new SeparateComponentStrategy(graph);

        // Create component
        final var locator = Locator.generate();
        final var component = new SimpleComponent(
                locator, componentType, new SimpleData(String.class, "Test"), timestamp, Optional.empty());

        // Store component
        strategy.store(component);

        // Find active component
        final var found = strategy.findActive(locator.id());
        assertTrue(found.isPresent());
        assertEquals(component, found.get());
    }

    @Test
    @DisplayName("SeparateComponentStrategy - Multiple versions")
    final void testSeparateComponentStrategyVersions() {
        final var strategy = new SeparateComponentStrategy(graph);
        final Uid id = UidFactory.generate();

        // Create and store multiple versions
        final var v1 = new SimpleComponent(
                new Locator(id, 1),
                componentType,
                new SimpleData(String.class, "V1"),
                timestamp,
                Optional.of(timestamp.plusSeconds(1)));
        final var v2 = new SimpleComponent(
                new Locator(id, 2),
                componentType,
                new SimpleData(String.class, "V2"),
                timestamp.plusSeconds(1),
                Optional.of(timestamp.plusSeconds(2)));
        final var v3 = new SimpleComponent(
                new Locator(id, 3),
                componentType,
                new SimpleData(String.class, "V3"),
                timestamp.plusSeconds(2),
                Optional.empty());

        strategy.store(v1);
        strategy.store(v2);
        strategy.store(v3);

        // Find active should return v3
        final var active = strategy.findActive(id);
        assertTrue(active.isPresent());
        assertEquals(3, active.get().locator().version());

        // Find versions should return all
        final var versions = strategy.findVersions(id);
        assertEquals(3, versions.size());
    }

    @Test
    @DisplayName("SeparateComponentStrategy - Update references")
    final void testSeparateComponentStrategyUpdateReferences() {
        final var strategy = new SeparateComponentStrategy(graph);

        // Create component
        final var oldLocator = Locator.generate();
        final var component = new SimpleComponent(
                oldLocator, componentType, new SimpleData(String.class, "Test"), timestamp, Optional.empty());
        strategy.store(component);

        // Create edge referencing component
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final var node1 = nodeOps.add(node1Id, defaultType, new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(node2Id, defaultType, new SimpleData(String.class, "Node2"), timestamp);

        final Set<Locator> components = new HashSet<>();
        components.add(oldLocator);
        final var edge =
                edgeOps.add(defaultType, node1, node2, new SimpleData(String.class, "Edge"), components, timestamp);

        // Update references is a no-op in SeparateComponentStrategy, but findEdgesReferencingComponent
        // still searches the graph for edges with the component reference
        final var newLocator = new Locator(oldLocator.id(), 2);
        strategy.updateReferences(oldLocator, newLocator);

        // The edge should still reference the old locator since updateReferences is a no-op
        final var edges = strategy.findEdgesReferencingComponent(oldLocator);
        assertEquals(1, edges.size());
        assertTrue(edges.contains(edge));
    }

    @Test
    @DisplayName("SeparateComponentStrategy - Get all active")
    final void testSeparateComponentStrategyAllActive() {
        final var strategy = new SeparateComponentStrategy(graph);

        // Create multiple components
        final var comp1 = new SimpleComponent(
                Locator.generate(), componentType, new SimpleData(String.class, "C1"), timestamp, Optional.empty());
        final var comp2 = new SimpleComponent(
                Locator.generate(), componentType, new SimpleData(String.class, "C2"), timestamp, Optional.empty());
        final var comp3 = new SimpleComponent(
                Locator.generate(),
                componentType,
                new SimpleData(String.class, "C3"),
                timestamp,
                Optional.of(timestamp.plusSeconds(1)));

        strategy.store(comp1);
        strategy.store(comp2);
        strategy.store(comp3);

        // Should return only active components (comp1 and comp2)
        final var active = strategy.allActive();
        assertEquals(2, active.size());
        assertTrue(active.contains(comp1));
        assertTrue(active.contains(comp2));
        assertFalse(active.contains(comp3));
    }

    @Test
    @DisplayName("GraphComponentStrategy - Store and retrieve component")
    final void testGraphComponentStrategyStore() {
        final var strategy = new GraphComponentStrategy(graph);

        // Create component
        final var locator = Locator.generate();
        final var component = new SimpleComponent(
                locator, componentType, new SimpleData(String.class, "Test"), timestamp, Optional.empty());

        // Store component
        strategy.store(component);

        // Find active component
        final var found = strategy.findActive(locator.id());
        assertTrue(found.isPresent());
        assertEquals(component, found.get());

        // Verify manifest node was created in graph
        final var manifestNode = graph.vertexSet().stream()
                .filter(n -> n.locator().id().equals(locator.id()))
                .findFirst();
        assertTrue(manifestNode.isPresent());
        // Manifest nodes use a special ComponentManifest type
        assertEquals(
                "SimpleType[code=ComponentManifest]", manifestNode.get().type().toString());
    }

    @Test
    @DisplayName("GraphComponentStrategy - Track edges referencing component")
    final void testGraphComponentStrategyTrackEdges() {
        final var strategy = new GraphComponentStrategy(graph);

        // Create component
        final var component = new SimpleComponent(
                Locator.generate(), componentType, new SimpleData(String.class, "Test"), timestamp, Optional.empty());
        strategy.store(component);

        // Create nodes and edge referencing component
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final var node1 = nodeOps.add(node1Id, defaultType, new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(node2Id, defaultType, new SimpleData(String.class, "Node2"), timestamp);

        final Set<Locator> components = new HashSet<>();
        components.add(component.locator());
        final var edge =
                edgeOps.add(defaultType, node1, node2, new SimpleData(String.class, "Edge"), components, timestamp);

        // Store edge reference
        strategy.updateReferences(null, component.locator());

        // Find edges should return the edge
        final var edges = strategy.findEdgesReferencingComponent(component.locator());
        assertEquals(1, edges.size());
        assertTrue(edges.contains(edge));
    }

    @Test
    @DisplayName("GraphComponentStrategy - Update references")
    final void testGraphComponentStrategyUpdateReferences() {
        final var strategy = new GraphComponentStrategy(graph);

        // Create component v1
        final Uid id = UidFactory.generate();
        final var v1Locator = new Locator(id, 1);
        final var v1 = new SimpleComponent(
                v1Locator, componentType, new SimpleData(String.class, "V1"), timestamp, Optional.empty());
        strategy.store(v1);

        // Create edge referencing v1
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final var node1 = nodeOps.add(node1Id, defaultType, new SimpleData(String.class, "Node1"), timestamp);
        final var node2 = nodeOps.add(node2Id, defaultType, new SimpleData(String.class, "Node2"), timestamp);

        final Set<Locator> components = new HashSet<>();
        components.add(v1Locator);
        edgeOps.add(defaultType, node1, node2, new SimpleData(String.class, "Edge"), components, timestamp);

        // Update to v2
        final var v2Locator = new Locator(id, 2);
        final var v2 = new SimpleComponent(
                v2Locator,
                componentType,
                new SimpleData(String.class, "V2"),
                timestamp.plusSeconds(1),
                Optional.empty());
        strategy.store(v2);
        strategy.updateReferences(v1Locator, v2Locator);

        // updateReferences is a no-op in GraphComponentStrategy, so edges still reference v1
        final var edgesV1 = strategy.findEdgesReferencingComponent(v1Locator);
        assertEquals(1, edgesV1.size());

        final var edgesV2 = strategy.findEdgesReferencingComponent(v2Locator);
        assertEquals(0, edgesV2.size());
    }

    @Test
    @DisplayName("GraphComponentStrategy - Clear")
    final void testGraphComponentStrategyClear() {
        final var strategy = new GraphComponentStrategy(graph);

        // Create and store components
        final var comp1 = new SimpleComponent(
                Locator.generate(), componentType, new SimpleData(String.class, "C1"), timestamp, Optional.empty());
        final var comp2 = new SimpleComponent(
                Locator.generate(), componentType, new SimpleData(String.class, "C2"), timestamp, Optional.empty());

        strategy.store(comp1);
        strategy.store(comp2);

        // Verify components exist
        assertEquals(2, strategy.allActive().size());

        // Clear
        strategy.clear();

        // Verify all components are removed
        assertEquals(0, strategy.allActive().size());
        assertFalse(strategy.findActive(comp1.locator().id()).isPresent());
        assertFalse(strategy.findActive(comp2.locator().id()).isPresent());
    }

    @Test
    @DisplayName("SeparateComponentStrategy - Clear")
    final void testSeparateComponentStrategyClear() {
        final var strategy = new SeparateComponentStrategy(graph);

        // Create and store components
        final var comp1 = new SimpleComponent(
                Locator.generate(), componentType, new SimpleData(String.class, "C1"), timestamp, Optional.empty());
        final var comp2 = new SimpleComponent(
                Locator.generate(), componentType, new SimpleData(String.class, "C2"), timestamp, Optional.empty());

        strategy.store(comp1);
        strategy.store(comp2);

        // Verify components exist
        assertEquals(2, strategy.allActive().size());

        // Clear
        strategy.clear();

        // Verify all components are removed
        assertEquals(0, strategy.allActive().size());
        assertFalse(strategy.findActive(comp1.locator().id()).isPresent());
        assertFalse(strategy.findActive(comp2.locator().id()).isPresent());
    }
}
