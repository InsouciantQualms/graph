/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.iq.common.fp.Fn1;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.simple.SimpleComponent;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Reference interface and its implementations.
 */
class ReferenceTest {

    @Test
    final void testLoadedNodeReference() {
        final var nodeId = new NanoId("test-node-id");
        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "Test Node");
        final var node = new SimpleNode(locator, List.of(), data, Instant.now(), Optional.empty());

        final var reference = new Reference.Loaded<>(node);

        assertEquals(locator, reference.locator());
        assertEquals(node, reference.value());
    }

    @Test
    final void testUnloadedNodeReference() {
        final var nodeId = new NanoId("test-node-id");
        final var locator = new Locator(nodeId, 1);

        final var reference = new Reference.Unloaded<>(locator, Node.class);

        assertEquals(locator, reference.locator());
        assertEquals(Node.class, reference.type());
    }

    @Test
    final void testUnloadedNodeReferenceLoad() {
        final var nodeId = new NanoId("test-node-id");
        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "Test Node");
        final var node = new SimpleNode(locator, List.of(), data, Instant.now(), Optional.empty());

        final Fn1<Locator, Node> loader = loc -> {
            assertEquals(locator, loc);
            return node;
        };

        final var unloaded = new Reference.Unloaded<>(locator, Node.class);
        final var loaded = unloaded.load(loader);

        assertNotNull(loaded);
        assertEquals(node, loaded.value());
        assertEquals(locator, loaded.locator());
    }

    @Test
    final void testLoadedEdgeReference() {
        final var sourceNode = createTestNode("source-node");
        final var targetNode = createTestNode("target-node");
        final var edgeId = new NanoId("test-edge-id");
        final var edgeLocator = new Locator(edgeId, 1);
        final var edgeData = new SimpleData(String.class, "Test Edge");
        final var edge = new SimpleEdge(
                edgeLocator,
                new Reference.Loaded<>(sourceNode),
                new Reference.Loaded<>(targetNode),
                edgeData,
                Instant.now(),
                Optional.empty());

        final var reference = new Reference.Loaded<>(edge);

        assertEquals(edgeLocator, reference.locator());
        assertEquals(edge, reference.value());
    }

    @Test
    final void testLoadedComponentReference() {
        final var componentId = new NanoId("test-component-id");
        final var locator = new Locator(componentId, 1);
        final var data = new SimpleData(String.class, "Test Component");
        final var component = new SimpleComponent(locator, List.of(), data, Instant.now(), Optional.empty());

        final var reference = new Reference.Loaded<>(component);

        assertEquals(locator, reference.locator());
        assertEquals(component, reference.value());
    }

    @Test
    final void testLoadedReferenceEquality() {
        final var node1 = createTestNode("test-node");
        final var node2 = createTestNode("test-node");

        final var ref1 = new Reference.Loaded<>(node1);
        final var ref2 = new Reference.Loaded<>(node2);

        assertEquals(ref1, ref2);
        assertEquals(ref1.hashCode(), ref2.hashCode());
    }

    @Test
    final void testLoadedReferenceInequality() {
        final var node1 = createTestNode("node-1");
        final var node2 = createTestNode("node-2");

        final var ref1 = new Reference.Loaded<>(node1);
        final var ref2 = new Reference.Loaded<>(node2);

        assertNotEquals(ref1, ref2);
    }

    @Test
    final void testUnloadedReferenceEquality() {
        final var nodeId = new NanoId("test-node-id");
        final var locator = new Locator(nodeId, 1);

        final var ref1 = new Reference.Unloaded<>(locator, Node.class);
        final var ref2 = new Reference.Unloaded<>(locator, Node.class);

        assertEquals(ref1, ref2);
        assertEquals(ref1.hashCode(), ref2.hashCode());
    }

    @Test
    final void testUnloadedReferenceInequality() {
        final var locator1 = new Locator(new NanoId("node-1"), 1);
        final var locator2 = new Locator(new NanoId("node-2"), 1);

        final var ref1 = new Reference.Unloaded<>(locator1, Node.class);
        final var ref2 = new Reference.Unloaded<>(locator2, Node.class);

        assertNotEquals(ref1, ref2);
    }

    @Test
    final void testLoadedUnloadedCrossEquality() {
        final var nodeId = new NanoId("test-node-id");
        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "Test Node");
        final var node = new SimpleNode(locator, List.of(), data, Instant.now(), Optional.empty());

        final var loaded = new Reference.Loaded<>(node);
        final var unloaded = new Reference.Unloaded<>(locator, Node.class);

        assertEquals(loaded, unloaded);
        assertEquals(unloaded, loaded);
        assertEquals(loaded.hashCode(), unloaded.hashCode());
    }

    @Test
    final void testReferenceNotEqualToOtherTypes() {
        final var node = createTestNode("test-node");
        final var reference = new Reference.Loaded<>(node);

        assertNotEquals(reference, node);
        assertNotEquals(reference, "string");
        assertNotEquals(reference, null);
        assertFalse(reference.equals(null));
    }

    @Test
    final void testUnloadedReferenceWithDifferentTypes() {
        final var locator = new Locator(new NanoId("test-id"), 1);

        final var nodeRef = new Reference.Unloaded<>(locator, Node.class);
        final var edgeRef = new Reference.Unloaded<>(locator, Edge.class);

        // They have the same locator, so they should be equal according to the current implementation
        assertEquals(nodeRef, edgeRef);
    }

    @Test
    final void testLoadWithException() {
        final var locator = new Locator(new NanoId("test-node-id"), 1);
        final Fn1<Locator, Node> failingLoader = loc -> {
            throw new RuntimeException("Loading failed");
        };

        final var unloaded = new Reference.Unloaded<>(locator, Node.class);

        assertThrows(RuntimeException.class, () -> unloaded.load(failingLoader));
    }

    @Test
    final void testRecordPatternMatching() {
        final var node = createTestNode("test-node");
        final Reference<Node> loadedRef = new Reference.Loaded<>(node);
        final Reference<Node> unloadedRef = new Reference.Unloaded<>(node.locator(), Node.class);

        // Test pattern matching for Loaded
        switch (loadedRef) {
            case Reference.Loaded<Node> loaded -> assertEquals(node, loaded.value());
            case Reference.Unloaded<Node> unloaded -> throw new AssertionError("Expected Loaded but got Unloaded");
        }

        // Test pattern matching for Unloaded
        switch (unloadedRef) {
            case Reference.Loaded<Node> loaded -> throw new AssertionError("Expected Unloaded but got Loaded");
            case Reference.Unloaded<Node> unloaded -> {
                assertEquals(node.locator(), unloaded.locator());
                assertEquals(Node.class, unloaded.type());
            }
        }
    }

    private SimpleNode createTestNode(final String id) {
        final var nodeId = new NanoId(id);
        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "Test Node: " + id);
        return new SimpleNode(locator, List.of(), data, Instant.now(), Optional.empty());
    }
}
