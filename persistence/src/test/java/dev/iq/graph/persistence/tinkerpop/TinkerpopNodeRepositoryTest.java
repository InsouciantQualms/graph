/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.tinkerpop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TinkerpopNodeRepository.
 */
@DisplayName("TinkerpopNodeRepository Unit Tests")
final class TinkerpopNodeRepositoryTest {

    private GraphTraversalSource traversalSource;
    private TinkerpopNodeRepository repository;

    @BeforeEach
    void before() {
        final Graph graph = TinkerGraph.open();
        traversalSource = graph.traversal();
        repository = new TinkerpopNodeRepository(graph);
    }

    @Test
    @DisplayName("save persists node in graph")
    void testSavePersistsNode() {

        final var node = createTestNode();

        final var result = repository.save(node);

        // Verify node was saved
        assertEquals(node, result);

        // Verify node exists in graph
        final var vertices = traversalSource
                .V()
                .hasLabel("node")
                .has("id", node.locator().id().id())
                .has("versionId", node.locator().version())
                .toList();

        assertEquals(1, vertices.size());
    }

    @Test
    @DisplayName("findActive returns active node")
    void testFindActiveReturnsNode() {

        final var nodeId = NanoId.generate();
        final var node = new SimpleNode(
                new Locator(nodeId, 1),
                new SimpleType("test"),
                new SimpleData(String.class, "test-value"),
                new HashSet<>(),
                Instant.now(),
                Optional.empty());

        repository.save(node);

        final var result = repository.findActive(nodeId);

        assertTrue(result.isPresent());
        assertEquals(nodeId, result.get().locator().id());
        assertEquals(1, result.get().locator().version());
        assertFalse(result.get().expired().isPresent());
    }

    @Test
    @DisplayName("findActive returns empty when no active node exists")
    void testFindActiveReturnsEmpty() {

        final var nodeId = NanoId.generate();
        final var expiredNode = new SimpleNode(
                new Locator(nodeId, 1),
                new SimpleType("test"),
                new SimpleData(String.class, "test-value"),
                new HashSet<>(),
                Instant.now(),
                Optional.of(Instant.now()));

        repository.save(expiredNode);

        final var result = repository.findActive(nodeId);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("findAll returns all versions of a node")
    void testFindAllReturnsAllVersions() {

        final var nodeId = NanoId.generate();
        final var node1 = new SimpleNode(
                new Locator(nodeId, 1),
                new SimpleType("test"),
                new SimpleData(String.class, "test-value-1"),
                new HashSet<>(),
                Instant.now(),
                Optional.of(Instant.now()));
        final var node2 = new SimpleNode(
                new Locator(nodeId, 2),
                new SimpleType("test"),
                new SimpleData(String.class, "test-value-2"),
                new HashSet<>(),
                Instant.now(),
                Optional.empty());

        repository.save(node1);
        repository.save(node2);

        final var results = repository.findAll(nodeId);

        assertEquals(2, results.size());
        assertEquals(1, results.get(0).locator().version());
        assertEquals(2, results.get(1).locator().version());
    }

    @Test
    @DisplayName("find returns specific version of node")
    void testFindReturnsSpecificVersion() {

        final var locator = new Locator(NanoId.generate(), 2);
        final var node = new SimpleNode(
                locator,
                new SimpleType("test"),
                new SimpleData(String.class, "test-value"),
                new HashSet<>(),
                Instant.now(),
                Optional.empty());

        repository.save(node);

        final var result = repository.find(locator);

        assertNotNull(result);
        assertEquals(locator, result.locator());
    }

    @Test
    @DisplayName("findAt returns node at specific timestamp")
    void testFindAtReturnsNodeAtTimestamp() {

        final var nodeId = NanoId.generate();
        final var timestamp = Instant.now();
        final var created = timestamp.minusSeconds(10);

        final var node = new SimpleNode(
                new Locator(nodeId, 1),
                new SimpleType("test"),
                new SimpleData(String.class, "test-value"),
                new HashSet<>(),
                created,
                Optional.empty());

        repository.save(node);

        final var result = repository.findAt(nodeId, timestamp);

        assertTrue(result.isPresent());
        assertEquals(nodeId, result.get().locator().id());
    }

    @Test
    @DisplayName("delete removes all versions of node")
    void testDeleteRemovesNode() {

        final var nodeId = NanoId.generate();
        final var node = new SimpleNode(
                new Locator(nodeId, 1),
                new SimpleType("test"),
                new SimpleData(String.class, "test-value"),
                new HashSet<>(),
                Instant.now(),
                Optional.empty());

        repository.save(node);

        final var result = repository.delete(nodeId);

        assertTrue(result);

        // Verify node is gone
        final var vertices =
                traversalSource.V().hasLabel("node").has("id", nodeId.id()).toList();

        assertTrue(vertices.isEmpty());
    }

    @Test
    @DisplayName("expire updates active node with expiration timestamp")
    void testExpireUpdatesNode() {

        final var nodeId = NanoId.generate();
        final var node = new SimpleNode(
                new Locator(nodeId, 1),
                new SimpleType("test"),
                new SimpleData(String.class, "test-value"),
                new HashSet<>(),
                Instant.now(),
                Optional.empty());

        repository.save(node);

        final var expiredAt = Instant.now();
        final var result = repository.expire(nodeId, expiredAt);

        assertTrue(result);

        // Verify node is expired
        final var foundNode = repository.find(new Locator(nodeId, 1));
        assertNotNull(foundNode);
        assertTrue(foundNode.expired().isPresent());
    }

    private static SimpleNode createTestNode() {

        final var locator = new Locator(NanoId.generate(), 1);
        final var data = new SimpleData(String.class, "test-value");
        return new SimpleNode(locator, new SimpleType("test"), data, new HashSet<>(), Instant.now(), Optional.empty());
    }
}
