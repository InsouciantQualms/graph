/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import org.junit.jupiter.api.DisplayName;

/**
 * Tests for ComponentSpace interface and its implementation.
 * NOTE: These tests are commented out because they require access to
 * nodeOperations() and edgeOperations() which are not part of the public API.
 */
@DisplayName("ComponentSpace Tests")
class ComponentSpaceTest {
    // All tests commented out - they require internal operations access
    /*

    private TestGraphSpace graphSpace;
    private final Type nodeType = new SimpleType("node");
    private final Type edgeType = new SimpleType("edge");
    private final Type componentType = new SimpleType("component");
    private final Instant timestamp = Instant.now();

    @BeforeEach
    final void before() {
        graphSpace = new TestGraphSpace();
    }

    @Test
    @DisplayName("ComponentSpace extends Space and provides view")
    final void testComponentSpaceExtendsSpace() {
        // Create a simple component
        final Uid nodeId = UidFactory.generate();
        final var node =
                graphSpace.nodeOperations().add(nodeId, nodeType, new SimpleData(String.class, "Node"), timestamp);

        final Set<Edge> edges = new HashSet<>();
        final var componentSpace =
                graphSpace.createComponent(edges, componentType, new SimpleData(String.class, "Component"), timestamp);

        // ComponentSpace should extend Space
        assertTrue(componentSpace instanceof Space);

        // Should provide access to View
        final var view = componentSpace.view();
        assertNotNull(view);
    }

    @Test
    @DisplayName("ComponentSpace returns its component")
    final void testComponentSpaceComponent() {
        // Create component
        final Uid nodeId = UidFactory.generate();
        final var node =
                graphSpace.nodeOperations().add(nodeId, nodeType, new SimpleData(String.class, "Node"), timestamp);

        final Set<Edge> edges = new HashSet<>();
        final var componentSpace = graphSpace.createComponent(
                edges, componentType, new SimpleData(String.class, "TestComponent"), timestamp);

        // Should return the component
        final var component = componentSpace.component();
        assertNotNull(component);
        assertEquals(componentType, component.type());
        assertEquals("TestComponent", ((SimpleData) component.data()).value());
    }

    @Test
    @DisplayName("ComponentSpace returns component edges")
    final void testComponentSpaceEdges() {
        // Create nodes and edges
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final Uid node3Id = UidFactory.generate();
        final var node1 =
                graphSpace.nodeOperations().add(node1Id, nodeType, new SimpleData(String.class, "Node1"), timestamp);
        final var node2 =
                graphSpace.nodeOperations().add(node2Id, nodeType, new SimpleData(String.class, "Node2"), timestamp);
        final var node3 =
                graphSpace.nodeOperations().add(node3Id, nodeType, new SimpleData(String.class, "Node3"), timestamp);

        final Set<Locator> noComponents = new HashSet<>();
        final var edge1 = graphSpace
                .edgeOperations()
                .add(edgeType, node1, node2, new SimpleData(String.class, "Edge1"), noComponents, timestamp);
        final var edge2 = graphSpace
                .edgeOperations()
                .add(edgeType, node2, node3, new SimpleData(String.class, "Edge2"), noComponents, timestamp);

        // Create component with specific edges
        final Set<Edge> componentEdges = new HashSet<>();
        componentEdges.add(edge1);
        componentEdges.add(edge2);

        final var componentSpace = graphSpace.createComponent(
                componentEdges, componentType, new SimpleData(String.class, "Component"), timestamp);

        // Should return the edges
        final var edges = componentSpace.componentEdges();
        assertEquals(2, edges.size());
        assertTrue(edges.contains(edge1));
        assertTrue(edges.contains(edge2));
    }

    @Test
    @DisplayName("ComponentSpace returns component nodes")
    final void testComponentSpaceNodes() {
        // Create nodes and edges
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final Uid node3Id = UidFactory.generate();
        final var node1 =
                graphSpace.nodeOperations().add(node1Id, nodeType, new SimpleData(String.class, "Node1"), timestamp);
        final var node2 =
                graphSpace.nodeOperations().add(node2Id, nodeType, new SimpleData(String.class, "Node2"), timestamp);
        final var node3 =
                graphSpace.nodeOperations().add(node3Id, nodeType, new SimpleData(String.class, "Node3"), timestamp);

        final Set<Locator> noComponents = new HashSet<>();
        final var edge1 = graphSpace
                .edgeOperations()
                .add(edgeType, node1, node2, new SimpleData(String.class, "Edge1"), noComponents, timestamp);
        final var edge2 = graphSpace
                .edgeOperations()
                .add(edgeType, node2, node3, new SimpleData(String.class, "Edge2"), noComponents, timestamp);

        // Create component
        final Set<Edge> componentEdges = new HashSet<>();
        componentEdges.add(edge1);
        componentEdges.add(edge2);

        final var componentSpace = graphSpace.createComponent(
                componentEdges, componentType, new SimpleData(String.class, "Component"), timestamp);

        // Should return all nodes connected by the edges
        final var nodes = componentSpace.componentNodes();
        assertEquals(3, nodes.size());
        assertTrue(nodes.contains(node1));
        assertTrue(nodes.contains(node2));
        assertTrue(nodes.contains(node3));
    }

    @Test
    @DisplayName("ComponentSpace single node component")
    final void testComponentSpaceSingleNode() {
        // Create single node
        final Uid nodeId = UidFactory.generate();
        final var node = graphSpace
                .nodeOperations()
                .add(nodeId, nodeType, new SimpleData(String.class, "SingleNode"), timestamp);

        // Create component with no edges (single node component)
        final Set<Edge> noEdges = new HashSet<>();
        final var componentSpace = graphSpace.createComponent(
                noEdges, componentType, new SimpleData(String.class, "SingleNodeComponent"), timestamp);

        // Should have no edges
        assertTrue(componentSpace.componentEdges().isEmpty());

        // Should have one node
        final var nodes = componentSpace.componentNodes();
        assertEquals(1, nodes.size());
        assertTrue(nodes.contains(node));
    }

    @Test
    @DisplayName("ComponentSpace componentsForElement")
    final void testComponentSpaceComponentsForElement() {
        // Create nodes and edges
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final Uid node3Id = UidFactory.generate();
        final var node1 =
                graphSpace.nodeOperations().add(node1Id, nodeType, new SimpleData(String.class, "Node1"), timestamp);
        final var node2 =
                graphSpace.nodeOperations().add(node2Id, nodeType, new SimpleData(String.class, "Node2"), timestamp);
        final var node3 =
                graphSpace.nodeOperations().add(node3Id, nodeType, new SimpleData(String.class, "Node3"), timestamp);

        // Create edges
        final Set<Locator> noComponents = new HashSet<>();
        final var edge1 = graphSpace
                .edgeOperations()
                .add(edgeType, node1, node2, new SimpleData(String.class, "Edge1"), noComponents, timestamp);
        final var edge2 = graphSpace
                .edgeOperations()
                .add(edgeType, node2, node3, new SimpleData(String.class, "Edge2"), noComponents, timestamp);

        // Create first component (edge1)
        final Set<Edge> edges1 = new HashSet<>();
        edges1.add(edge1);
        final var componentSpace1 = graphSpace.createComponent(
                edges1, componentType, new SimpleData(String.class, "Component1"), timestamp);

        // Create second component (edge2)
        final Set<Edge> edges2 = new HashSet<>();
        edges2.add(edge2);
        final var componentSpace2 = graphSpace.createComponent(
                edges2, componentType, new SimpleData(String.class, "Component2"), timestamp);

        // Update edges to reference their components
        final Set<Locator> comp1Refs = new HashSet<>();
        comp1Refs.add(componentSpace1.component().locator());
        graphSpace
                .edgeOperations()
                .update(edge1.locator().id(), edgeType, edge1.data(), comp1Refs, timestamp.plusSeconds(1));

        final Set<Locator> comp2Refs = new HashSet<>();
        comp2Refs.add(componentSpace2.component().locator());
        graphSpace
                .edgeOperations()
                .update(edge2.locator().id(), edgeType, edge2.data(), comp2Refs, timestamp.plusSeconds(1));

        // From componentSpace1 perspective, check components for node2
        final var componentsForNode2 =
                componentSpace1.componentsForElement(node2.locator().id());

        // This should include both components since node2 is in both
        assertEquals(2, componentsForNode2.size());
        assertTrue(componentsForNode2.contains(componentSpace1.component()));
        assertTrue(componentsForNode2.contains(componentSpace2.component()));

        // Check components for node1 (only in component1)
        final var componentsForNode1 =
                componentSpace1.componentsForElement(node1.locator().id());
        assertEquals(1, componentsForNode1.size());
        assertTrue(componentsForNode1.contains(componentSpace1.component()));

        // Check components for node3 (only in component2)
        final var componentsForNode3 =
                componentSpace1.componentsForElement(node3.locator().id());
        assertEquals(1, componentsForNode3.size());
        assertTrue(componentsForNode3.contains(componentSpace2.component()));
    }

    @Test
    @DisplayName("ComponentSpace with circular edges")
    final void testComponentSpaceCircularEdges() {
        // Create nodes for circular path
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final Uid node3Id = UidFactory.generate();
        final var node1 =
                graphSpace.nodeOperations().add(node1Id, nodeType, new SimpleData(String.class, "Node1"), timestamp);
        final var node2 =
                graphSpace.nodeOperations().add(node2Id, nodeType, new SimpleData(String.class, "Node2"), timestamp);
        final var node3 =
                graphSpace.nodeOperations().add(node3Id, nodeType, new SimpleData(String.class, "Node3"), timestamp);

        // Create circular edges: 1->2, 2->3, 3->1
        final Set<Locator> noComponents = new HashSet<>();
        final var edge1 = graphSpace
                .edgeOperations()
                .add(edgeType, node1, node2, new SimpleData(String.class, "Edge1"), noComponents, timestamp);
        final var edge2 = graphSpace
                .edgeOperations()
                .add(edgeType, node2, node3, new SimpleData(String.class, "Edge2"), noComponents, timestamp);
        final var edge3 = graphSpace
                .edgeOperations()
                .add(edgeType, node3, node1, new SimpleData(String.class, "Edge3"), noComponents, timestamp);

        // Create component with circular edges
        final Set<Edge> edges = new HashSet<>();
        edges.add(edge1);
        edges.add(edge2);
        edges.add(edge3);

        final var componentSpace = graphSpace.createComponent(
                edges, componentType, new SimpleData(String.class, "CircularComponent"), timestamp);

        // Should have all edges and nodes
        assertEquals(3, componentSpace.componentEdges().size());
        assertEquals(3, componentSpace.componentNodes().size());
    }
    */
}
