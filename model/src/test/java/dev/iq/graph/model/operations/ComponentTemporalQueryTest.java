/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

// import dev.iq.graph.model.TestComponentSpace;
// import dev.iq.graph.model.TestGraphSpace;
import org.junit.jupiter.api.DisplayName;

/**
 * Tests for temporal querying of components - verifying components can be recreated as of an Instant.
 * NOTE: These tests are commented out because they require TestGraphSpace and TestComponentSpace
 * which provide access to internal operations not available in the public API.
 */
@DisplayName("Component Temporal Query Tests")
class ComponentTemporalQueryTest {
    // All tests commented out - they require test-specific implementations
    /*

    private TestGraphSpace graphSpace;
    private final Type nodeType = new SimpleType("node");
    private final Type edgeType = new SimpleType("edge");
    private final Type componentType = new SimpleType("component");

    @BeforeEach
    final void before() {
        graphSpace = new TestGraphSpace();
    }

    @Test
    @DisplayName("Component state as of past Instant")
    final void testComponentAsOfPastInstant() {
        final var t1 = Instant.now();
        final var t2 = t1.plusSeconds(10);
        final var t3 = t2.plusSeconds(10);
        final var t4 = t3.plusSeconds(10);

        // Create initial graph at t1
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final Uid node3Id = UidFactory.generate();
        final var node1 = graphSpace.nodeOperations().add(node1Id, nodeType, new SimpleData(String.class, "Node1"), t1);
        final var node2 = graphSpace.nodeOperations().add(node2Id, nodeType, new SimpleData(String.class, "Node2"), t1);
        final var node3 = graphSpace.nodeOperations().add(node3Id, nodeType, new SimpleData(String.class, "Node3"), t1);

        // Create edges at t1
        final Set<Locator> noComponents = new HashSet<>();
        final var edge1 = graphSpace
                .edgeOperations()
                .add(edgeType, node1, node2, new SimpleData(String.class, "Edge1"), noComponents, t1);
        final var edge2 = graphSpace
                .edgeOperations()
                .add(edgeType, node2, node3, new SimpleData(String.class, "Edge2"), noComponents, t1);

        // Create component at t2
        final Set<Edge> edges = new HashSet<>();
        edges.add(edge1);
        edges.add(edge2);
        final var componentSpace =
                graphSpace.createComponent(edges, componentType, new SimpleData(String.class, "Component V1"), t2);
        final var componentId = componentSpace.component().locator().id();

        // Update component at t3
        graphSpace
                .componentOperations()
                .update(componentId, componentType, new SimpleData(String.class, "Component V2"), t3);

        // Query component state as of t2 (should see V1)
        final var componentAsOfT2 = graphSpace.componentOperations().findAt(componentId, t2);
        assertTrue(componentAsOfT2.isPresent());
        assertEquals("Component V1", ((SimpleData) componentAsOfT2.get().data()).value());
        assertEquals(1, componentAsOfT2.get().locator().version());

        // Query component state as of t3 (should see V2)
        final var componentAsOfT3 = graphSpace.componentOperations().findAt(componentId, t3);
        assertTrue(componentAsOfT3.isPresent());
        assertEquals("Component V2", ((SimpleData) componentAsOfT3.get().data()).value());
        assertEquals(2, componentAsOfT3.get().locator().version());

        // Query component state as of t1 (before creation, should not exist)
        final var componentAsOfT1 = graphSpace.componentOperations().findAt(componentId, t1);
        assertFalse(componentAsOfT1.isPresent());
    }

    @Test
    @DisplayName("Component edges as of Instant")
    final void testComponentEdgesAsOfInstant() {
        final var t1 = Instant.now();
        final var t2 = t1.plusSeconds(10);
        final var t3 = t2.plusSeconds(10);
        final var t4 = t3.plusSeconds(10);

        // Create nodes
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final Uid node3Id = UidFactory.generate();
        final Uid node4Id = UidFactory.generate();
        final var node1 = graphSpace.nodeOperations().add(node1Id, nodeType, new SimpleData(String.class, "Node1"), t1);
        final var node2 = graphSpace.nodeOperations().add(node2Id, nodeType, new SimpleData(String.class, "Node2"), t1);
        final var node3 = graphSpace.nodeOperations().add(node3Id, nodeType, new SimpleData(String.class, "Node3"), t1);
        final var node4 = graphSpace.nodeOperations().add(node4Id, nodeType, new SimpleData(String.class, "Node4"), t1);

        // Create initial edges at t1
        final Set<Locator> noComponents = new HashSet<>();
        final var edge1 = graphSpace
                .edgeOperations()
                .add(edgeType, node1, node2, new SimpleData(String.class, "Edge1"), noComponents, t1);
        final var edge2 = graphSpace
                .edgeOperations()
                .add(edgeType, node2, node3, new SimpleData(String.class, "Edge2"), noComponents, t1);

        // Create component at t2 with edges 1 and 2
        Set<Edge> edges = new HashSet<>();
        edges.add(edge1);
        edges.add(edge2);
        final var componentSpace =
                graphSpace.createComponent(edges, componentType, new SimpleData(String.class, "Component"), t2);

        // Add new edge at t3
        final var edge3 = graphSpace
                .edgeOperations()
                .add(edgeType, node3, node4, new SimpleData(String.class, "Edge3"), noComponents, t3);

        // Expire edge2 at t4
        graphSpace.edgeOperations().expire(edge2.locator().id(), t4);

        // Get ComponentSpace
        final var currentComponentSpace = (TestComponentSpace) graphSpace
                .componentSpace(componentSpace.component().locator().id())
                .orElseThrow();

        // Query edges as of t2 (should have edge1 and edge2)
        final var edgesAsOfT2 = currentComponentSpace.componentEdgesAsOf(t2);
        assertEquals(2, edgesAsOfT2.size());
        assertTrue(edgesAsOfT2.stream()
                .anyMatch(e -> e.locator().id().equals(edge1.locator().id())));
        assertTrue(edgesAsOfT2.stream()
                .anyMatch(e -> e.locator().id().equals(edge2.locator().id())));

        // Query edges as of t3 (still should have edge1 and edge2, edge3 not part of component)
        final var edgesAsOfT3 = currentComponentSpace.componentEdgesAsOf(t3);
        assertEquals(2, edgesAsOfT3.size());

        // Query edges as of t4 (edge2 expired, should only have edge1)
        final var edgesAsOfT4 = currentComponentSpace.componentEdgesAsOf(t4);
        assertEquals(1, edgesAsOfT4.size());
        assertTrue(edgesAsOfT4.stream()
                .anyMatch(e -> e.locator().id().equals(edge1.locator().id())));
        assertFalse(edgesAsOfT4.stream()
                .anyMatch(e -> e.locator().id().equals(edge2.locator().id())));
    }

    @Test
    @DisplayName("Component nodes as of Instant")
    final void testComponentNodesAsOfInstant() {
        final var t1 = Instant.now();
        final var t2 = t1.plusSeconds(10);
        final var t3 = t2.plusSeconds(10);
        final var t4 = t3.plusSeconds(10);

        // Create nodes
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final Uid node3Id = UidFactory.generate();
        final var node1 = graphSpace.nodeOperations().add(node1Id, nodeType, new SimpleData(String.class, "Node1"), t1);
        final var node2 = graphSpace.nodeOperations().add(node2Id, nodeType, new SimpleData(String.class, "Node2"), t1);
        final var node3 = graphSpace.nodeOperations().add(node3Id, nodeType, new SimpleData(String.class, "Node3"), t1);

        // Create edges
        final Set<Locator> noComponents = new HashSet<>();
        final var edge1 = graphSpace
                .edgeOperations()
                .add(edgeType, node1, node2, new SimpleData(String.class, "Edge1"), noComponents, t1);
        final var edge2 = graphSpace
                .edgeOperations()
                .add(edgeType, node2, node3, new SimpleData(String.class, "Edge2"), noComponents, t1);

        // Create component at t2
        final Set<Edge> edges = new HashSet<>();
        edges.add(edge1);
        edges.add(edge2);
        final var componentSpace =
                graphSpace.createComponent(edges, componentType, new SimpleData(String.class, "Component"), t2);

        // Update node2 data at t3
        graphSpace.nodeOperations().update(node2Id, nodeType, new SimpleData(String.class, "Node2 Updated"), t3);

        // Expire node3 at t4 (which should cascade to edge2)
        graphSpace.nodeOperations().expire(node3Id, t4);

        // Get ComponentSpace
        final var currentComponentSpace = (TestComponentSpace) graphSpace
                .componentSpace(componentSpace.component().locator().id())
                .orElseThrow();

        // Query nodes as of t2 (should have all 3 nodes)
        final var nodesAsOfT2 = currentComponentSpace.componentNodesAsOf(t2);
        assertEquals(3, nodesAsOfT2.size());
        assertTrue(nodesAsOfT2.stream().anyMatch(n -> n.locator().id().equals(node1Id)));
        assertTrue(nodesAsOfT2.stream().anyMatch(n -> n.locator().id().equals(node2Id)));
        assertTrue(nodesAsOfT2.stream().anyMatch(n -> n.locator().id().equals(node3Id)));

        // Verify node2 has original data at t2
        final var node2AtT2 = nodesAsOfT2.stream()
                .filter(n -> n.locator().id().equals(node2Id))
                .findFirst()
                .orElseThrow();
        assertEquals("Node2", ((SimpleData) node2AtT2.data()).value());

        // Query nodes as of t3 (should have updated node2)
        final var nodesAsOfT3 = currentComponentSpace.componentNodesAsOf(t3);
        assertEquals(3, nodesAsOfT3.size());
        final var node2AtT3 = nodesAsOfT3.stream()
                .filter(n -> n.locator().id().equals(node2Id))
                .findFirst()
                .orElseThrow();
        assertEquals("Node2 Updated", ((SimpleData) node2AtT3.data()).value());

        // Query nodes as of t4 (node3 expired, edge2 cascaded expired, should only have node1 and node2)
        final var nodesAsOfT4 = currentComponentSpace.componentNodesAsOf(t4);
        assertEquals(2, nodesAsOfT4.size());
        assertTrue(nodesAsOfT4.stream().anyMatch(n -> n.locator().id().equals(node1Id)));
        assertTrue(nodesAsOfT4.stream().anyMatch(n -> n.locator().id().equals(node2Id)));
        assertFalse(nodesAsOfT4.stream().anyMatch(n -> n.locator().id().equals(node3Id)));
    }

    @Test
    @DisplayName("Component recreation exactly as it existed at past Instant")
    final void testComponentRecreationAtInstant() {
        final var t1 = Instant.now();
        final var t2 = t1.plusSeconds(10);
        final var t3 = t2.plusSeconds(10);
        final var t4 = t3.plusSeconds(10);
        final var t5 = t4.plusSeconds(10);

        // Create complex component evolution
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final Uid node3Id = UidFactory.generate();
        final Uid node4Id = UidFactory.generate();

        // t1: Create initial nodes
        final var node1 =
                graphSpace.nodeOperations().add(node1Id, nodeType, new SimpleData(String.class, "Node1 V1"), t1);
        final var node2 =
                graphSpace.nodeOperations().add(node2Id, nodeType, new SimpleData(String.class, "Node2 V1"), t1);
        final var node3 =
                graphSpace.nodeOperations().add(node3Id, nodeType, new SimpleData(String.class, "Node3 V1"), t1);

        // t1: Create initial edges
        final Set<Locator> noComponents = new HashSet<>();
        final var edge1 = graphSpace
                .edgeOperations()
                .add(edgeType, node1, node2, new SimpleData(String.class, "Edge1 V1"), noComponents, t1);
        final var edge2 = graphSpace
                .edgeOperations()
                .add(edgeType, node2, node3, new SimpleData(String.class, "Edge2 V1"), noComponents, t1);

        // t2: Create component
        final Set<Edge> edges = new HashSet<>();
        edges.add(edge1);
        edges.add(edge2);
        final var componentSpace =
                graphSpace.createComponent(edges, componentType, new SimpleData(String.class, "Component V1"), t2);
        final var componentId = componentSpace.component().locator().id();

        // t3: Update component data
        graphSpace
                .componentOperations()
                .update(componentId, componentType, new SimpleData(String.class, "Component V2"), t3);

        // t4: Add node4 and edge3
        final var node4 =
                graphSpace.nodeOperations().add(node4Id, nodeType, new SimpleData(String.class, "Node4 V1"), t4);
        final var edge3 = graphSpace
                .edgeOperations()
                .add(edgeType, node3, node4, new SimpleData(String.class, "Edge3 V1"), noComponents, t4);

        // t5: Update node2 and edge1
        graphSpace.nodeOperations().update(node2Id, nodeType, new SimpleData(String.class, "Node2 V2"), t5);
        graphSpace
                .edgeOperations()
                .update(edge1.locator().id(), edgeType, new SimpleData(String.class, "Edge1 V2"), noComponents, t5);

        // Now recreate component as of different times
        final var currentComponentSpace =
                (TestComponentSpace) graphSpace.componentSpace(componentId).orElseThrow();

        // As of t2: Initial state
        final var componentAtT2 =
                graphSpace.componentOperations().findAt(componentId, t2).orElseThrow();
        assertEquals("Component V1", ((SimpleData) componentAtT2.data()).value());

        final var nodesAtT2 = currentComponentSpace.componentNodesAsOf(t2);
        assertEquals(3, nodesAtT2.size());
        assertTrue(nodesAtT2.stream()
                .allMatch(n -> ((SimpleData) n.data()).value().toString().endsWith("V1")));

        final var edgesAtT2 = currentComponentSpace.componentEdgesAsOf(t2);
        assertEquals(2, edgesAtT2.size());
        assertTrue(edgesAtT2.stream()
                .allMatch(e -> ((SimpleData) e.data()).value().toString().endsWith("V1")));

        // As of t5: Latest state with updates
        final var componentAtT5 =
                graphSpace.componentOperations().findAt(componentId, t5).orElseThrow();
        assertEquals("Component V2", ((SimpleData) componentAtT5.data()).value());

        final var nodesAtT5 = currentComponentSpace.componentNodesAsOf(t5);
        assertEquals(3, nodesAtT5.size()); // Still 3 nodes (node4 not part of component)

        // Verify node2 is updated
        final var node2AtT5 = nodesAtT5.stream()
                .filter(n -> n.locator().id().equals(node2Id))
                .findFirst()
                .orElseThrow();
        assertEquals("Node2 V2", ((SimpleData) node2AtT5.data()).value());

        // Verify edge1 is updated
        final var edgesAtT5 = currentComponentSpace.componentEdgesAsOf(t5);
        final var edge1AtT5 = edgesAtT5.stream()
                .filter(e -> e.locator().id().equals(edge1.locator().id()))
                .findFirst()
                .orElseThrow();
        assertEquals("Edge1 V2", ((SimpleData) edge1AtT5.data()).value());
    }

    @Test
    @DisplayName("Component with expired and recreated elements")
    final void testComponentWithExpiredAndRecreatedElements() {
        final var t1 = Instant.now();
        final var t2 = t1.plusSeconds(10);
        final var t3 = t2.plusSeconds(10);
        final var t4 = t3.plusSeconds(10);

        // Create nodes and edges
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final var node1 = graphSpace.nodeOperations().add(node1Id, nodeType, new SimpleData(String.class, "Node1"), t1);
        final var node2 = graphSpace.nodeOperations().add(node2Id, nodeType, new SimpleData(String.class, "Node2"), t1);

        final Set<Locator> noComponents = new HashSet<>();
        final var edge1 = graphSpace
                .edgeOperations()
                .add(edgeType, node1, node2, new SimpleData(String.class, "Edge1"), noComponents, t1);

        // Create component at t1
        final Set<Edge> edges = new HashSet<>();
        edges.add(edge1);
        final var componentSpace =
                graphSpace.createComponent(edges, componentType, new SimpleData(String.class, "Component"), t1);

        // Expire component at t2
        graphSpace
                .componentOperations()
                .expire(componentSpace.component().locator().id(), t2);

        // Query as of t1 (should exist)
        final var componentAtT1 = graphSpace
                .componentOperations()
                .findAt(componentSpace.component().locator().id(), t1);
        assertTrue(componentAtT1.isPresent());
        assertTrue(componentAtT1.get().expired().isEmpty());

        // Query as of t2 (should be expired)
        final var componentAtT2 = graphSpace
                .componentOperations()
                .findAt(componentSpace.component().locator().id(), t2);
        assertTrue(componentAtT2.isPresent());
        assertTrue(componentAtT2.get().expired().isPresent());
        assertEquals(t2, componentAtT2.get().expired().get());

        // Query as of t3 (should still be expired)
        final var componentAtT3 = graphSpace
                .componentOperations()
                .findAt(componentSpace.component().locator().id(), t3);
        assertTrue(componentAtT3.isPresent());
        assertTrue(componentAtT3.get().expired().isPresent());
    }
    */
}
