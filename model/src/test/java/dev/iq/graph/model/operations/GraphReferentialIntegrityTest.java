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
import dev.iq.graph.model.jgrapht.JGraphtComponentOperations;
import dev.iq.graph.model.jgrapht.JGraphtComponentSpace;
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
 * Tests for graph referential integrity constraints.
 * - Each update() expires the current version and then adds an incremented version
 * - When a node is expired (directly or via an update), its incoming and outgoing edges are also expired
 * - When a component is updated (its Data or Type), the subgraph remains valid both before and after the update
 * - Components should be recreatable as of an Instant (timestamp) to accurately recreate the subgraph
 * - When a component's edges or nodes are modified, the component must be recreatable as of an Instant
 */
@DisplayName("Graph Referential Integrity Tests")
class GraphReferentialIntegrityTest {

    private Graph<Node, Edge> graph;
    private JGraphtNodeOperations nodeOps;
    private JGraphtEdgeOperations edgeOps;
    private JGraphtComponentOperations componentOps;
    private final Type nodeType = new SimpleType("node");
    private final Type edgeType = new SimpleType("edge");
    private final Type componentType = new SimpleType("component");

    @BeforeEach
    final void before() {
        graph = new DirectedMultigraph<>(null, null, true); // Allow self-loops
        edgeOps = new JGraphtEdgeOperations(graph);
        nodeOps = new JGraphtNodeOperations(graph, edgeOps);
        componentOps = new JGraphtComponentOperations(graph, nodeOps, edgeOps);
    }

    @Test
    @DisplayName("Update operations expire current version and create incremented version")
    final void testUpdateExpiresAndIncrements() {
        final Instant t1 = Instant.now();
        final Instant t2 = t1.plusSeconds(1);
        final Uid nodeId = UidFactory.generate();

        // Create initial node
        final var node1 = nodeOps.add(nodeId, nodeType, new SimpleData(String.class, "v1"), t1);
        assertEquals(1, node1.locator().version());
        assertFalse(node1.expired().isPresent());

        // Update node
        final var node2 = nodeOps.update(nodeId, nodeType, new SimpleData(String.class, "v2"), t2);
        assertEquals(2, node2.locator().version());
        assertFalse(node2.expired().isPresent());

        // Check that v1 is expired
        final var expiredNode = graph.vertexSet().stream()
                .filter(n -> n.locator().id().equals(nodeId) && n.locator().version() == 1)
                .findFirst()
                .orElseThrow();
        assertTrue(expiredNode.expired().isPresent());
        assertEquals(t2, expiredNode.expired().get());
    }

    @Test
    @DisplayName("Node expiration cascades to edges")
    final void testNodeExpirationCascadesToEdges() {
        final Instant t1 = Instant.now();
        final Instant t2 = t1.plusSeconds(1);
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final Uid node3Id = UidFactory.generate();

        // Create nodes
        final var node1 = nodeOps.add(node1Id, nodeType, new SimpleData(String.class, "n1"), t1);
        final var node2 = nodeOps.add(node2Id, nodeType, new SimpleData(String.class, "n2"), t1);
        final var node3 = nodeOps.add(node3Id, nodeType, new SimpleData(String.class, "n3"), t1);

        // Create edges
        final var edge12 =
                edgeOps.add(edgeType, node1, node2, new SimpleData(String.class, "e12"), new HashSet<>(), t1);
        final var edge23 =
                edgeOps.add(edgeType, node2, node3, new SimpleData(String.class, "e23"), new HashSet<>(), t1);
        final var edge31 =
                edgeOps.add(edgeType, node3, node1, new SimpleData(String.class, "e31"), new HashSet<>(), t1);

        // Expire node2
        nodeOps.expire(node2Id, t2);

        // Check that edges connected to node2 are expired
        final var edges = graph.edgeSet();

        // edge12 (incoming to node2) should be expired
        final var expiredEdge12 = edges.stream()
                .filter(e -> e.locator().id().equals(edge12.locator().id()))
                .findFirst()
                .orElseThrow();
        assertTrue(expiredEdge12.expired().isPresent());
        assertEquals(t2, expiredEdge12.expired().get());

        // edge23 (outgoing from node2) should be expired
        final var expiredEdge23 = edges.stream()
                .filter(e -> e.locator().id().equals(edge23.locator().id()))
                .findFirst()
                .orElseThrow();
        assertTrue(expiredEdge23.expired().isPresent());
        assertEquals(t2, expiredEdge23.expired().get());

        // edge31 (not connected to node2) should NOT be expired
        final var activeEdge31 = edges.stream()
                .filter(e -> e.locator().id().equals(edge31.locator().id()))
                .findFirst()
                .orElseThrow();
        assertFalse(activeEdge31.expired().isPresent());
    }

    @Test
    @DisplayName("Node update cascades to edges with proper versioning")
    final void testNodeUpdateCascadesToEdges() {
        final Instant t1 = Instant.now();
        final Instant t2 = t1.plusSeconds(1);
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();

        // Create nodes
        final var node1 = nodeOps.add(node1Id, nodeType, new SimpleData(String.class, "n1"), t1);
        final var node2 = nodeOps.add(node2Id, nodeType, new SimpleData(String.class, "n2"), t1);

        // Create edge
        final var edge = edgeOps.add(edgeType, node1, node2, new SimpleData(String.class, "e12"), new HashSet<>(), t1);

        // Update node1
        final var updatedNode1 = nodeOps.update(node1Id, nodeType, new SimpleData(String.class, "n1-updated"), t2);

        // Verify node1 was updated
        assertEquals(2, updatedNode1.locator().version());

        // Verify edge was recreated (old one expired, new one created)
        final var edges = graph.edgeSet();
        assertEquals(2, edges.size()); // One expired, one active

        // Find the active edge
        final var activeEdge =
                edges.stream().filter(e -> e.expired().isEmpty()).findFirst().orElseThrow();

        // The edge should connect the updated node1 to node2
        assertEquals(updatedNode1.locator().id(), activeEdge.source().locator().id());
        assertEquals(2, activeEdge.source().locator().version());
        assertEquals(node2.locator().id(), activeEdge.target().locator().id());
        assertEquals(t2, activeEdge.created());
    }

    @Test
    @DisplayName("Component update maintains subgraph validity")
    final void testComponentUpdateMaintainsSubgraphValidity() {
        final Instant t1 = Instant.now();
        final Instant t2 = t1.plusSeconds(1);
        final Instant t3 = t1.plusSeconds(2);

        // Create component
        final var component = componentOps.add(componentType, new SimpleData(String.class, "comp-v1"), t1);

        // Create nodes
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final var node1 = nodeOps.add(node1Id, nodeType, new SimpleData(String.class, "n1"), t2);
        final var node2 = nodeOps.add(node2Id, nodeType, new SimpleData(String.class, "n2"), t2);

        // Create edge with component reference
        final Set<Locator> components = new HashSet<>();
        components.add(component.locator());
        final var edge = edgeOps.add(edgeType, node1, node2, new SimpleData(String.class, "e12"), components, t2);

        // Create component space to verify subgraph before update
        final var spaceBefore = new JGraphtComponentSpace(component, graph, componentOps);
        assertEquals(1, spaceBefore.componentEdges().size());
        assertEquals(2, spaceBefore.componentNodes().size());

        // Update component
        final var updatedComponent = componentOps.update(
                component.locator().id(), componentType, new SimpleData(String.class, "comp-v2"), t3);

        // Create component space to verify subgraph after update
        final var spaceAfter = new JGraphtComponentSpace(updatedComponent, graph, componentOps);

        // The subgraph should still be valid with same structure
        assertEquals(1, spaceAfter.componentEdges().size());
        assertEquals(2, spaceAfter.componentNodes().size());

        // Verify edges now reference the updated component
        final var updatedEdge = spaceAfter.componentEdges().iterator().next();
        assertTrue(updatedEdge.components().contains(updatedComponent.locator()));
        assertFalse(updatedEdge.components().contains(component.locator()));
    }

    @Test
    @DisplayName("Component state recreatable as of an Instant")
    final void testComponentRecreationAsOfInstant() {
        final Instant t1 = Instant.now();
        final Instant t2 = t1.plusSeconds(1);
        final Instant t3 = t1.plusSeconds(2);
        final Instant t4 = t1.plusSeconds(3);

        // Create component
        final var component = componentOps.add(componentType, new SimpleData(String.class, "comp"), t1);

        // Create initial graph structure at t2
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final var node1 = nodeOps.add(node1Id, nodeType, new SimpleData(String.class, "n1"), t2);
        final var node2 = nodeOps.add(node2Id, nodeType, new SimpleData(String.class, "n2"), t2);

        final Set<Locator> components = new HashSet<>();
        components.add(component.locator());
        final var edge1 = edgeOps.add(edgeType, node1, node2, new SimpleData(String.class, "e12"), components, t2);

        // Add another node and edge at t3
        final Uid node3Id = UidFactory.generate();
        final var node3 = nodeOps.add(node3Id, nodeType, new SimpleData(String.class, "n3"), t3);
        final var edge2 = edgeOps.add(edgeType, node2, node3, new SimpleData(String.class, "e23"), components, t3);

        // Create component space
        final var space = new JGraphtComponentSpace(component, graph, componentOps);

        // Verify component state at t2 (before second edge was added)
        final var edgesAtT2 = space.componentEdgesAsOf(t2);
        assertEquals(1, edgesAtT2.size());
        assertTrue(edgesAtT2.stream()
                .anyMatch(e -> e.locator().id().equals(edge1.locator().id())));

        final var nodesAtT2 = space.componentNodesAsOf(t2);
        assertEquals(2, nodesAtT2.size());

        // Verify component state at t3 (after second edge was added)
        final var edgesAtT3 = space.componentEdgesAsOf(t3);
        assertEquals(2, edgesAtT3.size());

        final var nodesAtT3 = space.componentNodesAsOf(t3);
        assertEquals(3, nodesAtT3.size());

        // Update node2 at t4 (this should affect edges)
        nodeOps.update(node2Id, nodeType, new SimpleData(String.class, "n2-updated"), t4);

        // Verify component state at t3 still shows original structure
        final var edgesStillAtT3 = space.componentEdgesAsOf(t3);
        assertEquals(2, edgesStillAtT3.size());
        // All edges at t3 should be the expired versions
        assertTrue(edgesStillAtT3.stream().allMatch(e -> e.created().isBefore(t4)));
    }

    @Test
    @DisplayName("Component remains recreatable after edge modifications")
    final void testComponentRecreationAfterEdgeModifications() {
        final Instant t1 = Instant.now();
        final Instant t2 = t1.plusSeconds(1);
        final Instant t3 = t1.plusSeconds(2);
        final Instant t4 = t1.plusSeconds(3);

        // Create component
        final var component = componentOps.add(componentType, new SimpleData(String.class, "comp"), t1);

        // Create nodes
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final var node1 = nodeOps.add(node1Id, nodeType, new SimpleData(String.class, "n1"), t2);
        final var node2 = nodeOps.add(node2Id, nodeType, new SimpleData(String.class, "n2"), t2);

        // Create edge with component
        final Set<Locator> components = new HashSet<>();
        components.add(component.locator());
        final var edge = edgeOps.add(edgeType, node1, node2, new SimpleData(String.class, "e12-v1"), components, t2);

        // Update edge at t3
        final var updatedEdge =
                edgeOps.update(edge.locator().id(), edgeType, new SimpleData(String.class, "e12-v2"), components, t3);

        // Create component space
        final var space = new JGraphtComponentSpace(component, graph, componentOps);

        // Verify component state at t2 shows original edge
        final var edgesAtT2 = space.componentEdgesAsOf(t2);
        assertEquals(1, edgesAtT2.size());
        final var edgeAtT2 = edgesAtT2.iterator().next();
        assertEquals("e12-v1", ((SimpleData) edgeAtT2.data()).value());

        // Verify component state at t3 shows updated edge
        final var edgesAtT3 = space.componentEdgesAsOf(t3);
        assertEquals(1, edgesAtT3.size());
        final var edgeAtT3 = edgesAtT3.iterator().next();
        assertEquals("e12-v2", ((SimpleData) edgeAtT3.data()).value());

        // Expire edge at t4
        edgeOps.expire(edge.locator().id(), t4);

        // Verify component state at t3 still shows the edge
        final var edgesStillAtT3 = space.componentEdgesAsOf(t3);
        assertEquals(1, edgesStillAtT3.size());

        // Verify component state at t4 shows no edges (expired)
        final var edgesAtT4 = space.componentEdgesAsOf(t4);
        assertEquals(0, edgesAtT4.size());
    }

    @Test
    @DisplayName("Complex referential integrity scenario")
    final void testComplexReferentialIntegrity() {
        final Instant t1 = Instant.now();
        final Instant t2 = t1.plusSeconds(1);
        final Instant t3 = t1.plusSeconds(2);
        final Instant t4 = t1.plusSeconds(3);

        // Create two components
        final var comp1 = componentOps.add(componentType, new SimpleData(String.class, "comp1"), t1);
        final var comp2 = componentOps.add(componentType, new SimpleData(String.class, "comp2"), t1);

        // Create nodes
        final Uid node1Id = UidFactory.generate();
        final Uid node2Id = UidFactory.generate();
        final Uid node3Id = UidFactory.generate();
        final var node1 = nodeOps.add(node1Id, nodeType, new SimpleData(String.class, "n1"), t2);
        final var node2 = nodeOps.add(node2Id, nodeType, new SimpleData(String.class, "n2"), t2);
        final var node3 = nodeOps.add(node3Id, nodeType, new SimpleData(String.class, "n3"), t2);

        // Create edges with different component combinations
        final Set<Locator> comp1Set = Set.of(comp1.locator());
        final Set<Locator> comp2Set = Set.of(comp2.locator());
        final Set<Locator> bothComps = Set.of(comp1.locator(), comp2.locator());

        final var edge12 = edgeOps.add(edgeType, node1, node2, new SimpleData(String.class, "e12"), comp1Set, t2);
        final var edge23 = edgeOps.add(edgeType, node2, node3, new SimpleData(String.class, "e23"), bothComps, t2);
        final var edge31 = edgeOps.add(edgeType, node3, node1, new SimpleData(String.class, "e31"), comp2Set, t2);

        // Update comp1 at t3
        final var updatedComp1 = componentOps.update(
                comp1.locator().id(), componentType, new SimpleData(String.class, "comp1-updated"), t3);

        // Verify edges were updated correctly
        final var currentEdges =
                graph.edgeSet().stream().filter(e -> e.expired().isEmpty()).toList();

        // edge12 should reference updated comp1
        final var currentEdge12 = currentEdges.stream()
                .filter(e -> "e12".equals(((SimpleData) e.data()).value()))
                .findFirst()
                .orElseThrow();
        assertTrue(currentEdge12.components().contains(updatedComp1.locator()));
        assertEquals(1, currentEdge12.components().size());

        // edge23 should reference both updated comp1 and original comp2
        final var currentEdge23 = currentEdges.stream()
                .filter(e -> "e23".equals(((SimpleData) e.data()).value()))
                .findFirst()
                .orElseThrow();
        assertTrue(currentEdge23.components().contains(updatedComp1.locator()));
        assertTrue(currentEdge23.components().contains(comp2.locator()));
        assertEquals(2, currentEdge23.components().size());

        // edge31 should only reference comp2 (unchanged)
        final var currentEdge31 = currentEdges.stream()
                .filter(e -> "e31".equals(((SimpleData) e.data()).value()))
                .findFirst()
                .orElseThrow();
        assertFalse(currentEdge31.components().contains(updatedComp1.locator()));
        assertTrue(currentEdge31.components().contains(comp2.locator()));
        assertEquals(1, currentEdge31.components().size());

        // Update node2 at t4 (should affect all edges connected to it)
        nodeOps.update(node2Id, nodeType, new SimpleData(String.class, "n2-updated"), t4);

        // All edges should be recreated since they all connect to node2
        final var finalEdges =
                graph.edgeSet().stream().filter(e -> e.expired().isEmpty()).toList();

        assertEquals(3, finalEdges.size());

        // Remove debug output - was just for debugging

        // Only edges connected to node2 should be recreated at t4
        // edge31 connects node3 to node1, so it might not be recreated
        final var edgesConnectedToNode2 = finalEdges.stream()
                .filter(e -> e.source().locator().id().equals(node2Id)
                        || e.target().locator().id().equals(node2Id))
                .toList();
        assertTrue(edgesConnectedToNode2.stream().allMatch(e -> e.created().equals(t4)));
    }
}
