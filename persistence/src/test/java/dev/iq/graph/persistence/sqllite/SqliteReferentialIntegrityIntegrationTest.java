/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.simple.SimpleComponent;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests demonstrating the complete referential integrity system for SQLite.
 */
@DisplayName("SQLite Referential Integrity Integration Tests")
class SqliteReferentialIntegrityIntegrationTest {

    private DataSource dataSource;
    private SqliteHandleProvider session;
    private SqliteNodeRepositoryWithIntegrity nodeRepository;
    private SqliteEdgeRepository edgeRepository;
    private SqliteComponentRepositoryWithIntegrity componentRepository;
    private SqliteReferentialIntegrityManager integrityManager;

    private final Type defaultType = new SimpleType("test");
    private final Type componentType = new SimpleType("component");

    @BeforeEach
    void before() {
        // Create isolated in-memory database to avoid locking issues
        final var config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite::memory:");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1); // Use single connection to avoid locking issues
        config.setMinimumIdle(1);
        dataSource = new HikariDataSource(config);

        // Use session factory to properly initialize
        final var sessionFactory = new SqliteSessionFactory(dataSource);
        final var sqliteSession = sessionFactory.create();

        if (sqliteSession instanceof SqliteSession) {
            session = (SqliteSession) sqliteSession;
        } else {
            throw new IllegalStateException("Expected SqliteSession but got: " + sqliteSession.getClass());
        }

        // Create base repositories
        final var baseNodeRepo = new SqliteNodeRepository(session);
        final var baseEdgeRepo = new SqliteEdgeRepository(session, baseNodeRepo);
        final var baseComponentRepo = new SqliteComponentRepository(session);

        // Create integrity manager
        integrityManager =
                new SqliteReferentialIntegrityManager(session, baseNodeRepo, baseEdgeRepo, baseComponentRepo);

        // Create repositories with integrity support
        nodeRepository = new SqliteNodeRepositoryWithIntegrity(baseNodeRepo, integrityManager);
        edgeRepository = baseEdgeRepo;
        componentRepository = new SqliteComponentRepositoryWithIntegrity(baseComponentRepo, integrityManager);
    }

    @AfterEach
    void after() {
        // Close the data source to clean up connections
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }

    @Test
    @DisplayName("Complete workflow: Create graph, update component, verify cascade")
    void testCompleteReferentialIntegrityWorkflow() {
        final var baseTime = Instant.now();

        // Step 1: Create components
        final var comp1 = new SimpleComponent(
                new Locator(NanoId.generate(), 1),
                componentType,
                new TestData("Component1"),
                baseTime,
                Optional.empty());
        componentRepository.save(comp1);

        final var comp2 = new SimpleComponent(
                new Locator(NanoId.generate(), 1),
                componentType,
                new TestData("Component2"),
                baseTime,
                Optional.empty());
        componentRepository.save(comp2);

        // Step 2: Create nodes with component references
        final var node1 = new SimpleNode(
                new Locator(NanoId.generate(), 1),
                defaultType,
                new TestData("Node1"),
                Set.of(comp1.locator()),
                baseTime,
                Optional.empty());
        nodeRepository.save(node1);

        final var node2 = new SimpleNode(
                new Locator(NanoId.generate(), 1),
                defaultType,
                new TestData("Node2"),
                Set.of(comp1.locator(), comp2.locator()),
                baseTime,
                Optional.empty());
        nodeRepository.save(node2);

        // Step 3: Create edges
        final var edge1 = new SimpleEdge(
                new Locator(NanoId.generate(), 1),
                defaultType,
                node1,
                node2,
                new TestData("Edge1"),
                Set.of(comp1.locator()),
                baseTime,
                Optional.empty());
        edgeRepository.save(edge1);

        // Step 4: Update component1 (should cascade to all elements)
        final var updateTime = baseTime.plusSeconds(10);
        final var updatedComp1 = new SimpleComponent(
                new Locator(comp1.locator().id(), 2),
                componentType,
                new TestData("UpdatedComponent1"),
                updateTime,
                Optional.empty());

        // Use the update method which handles integrity
        componentRepository.update(comp1, updatedComp1, updateTime);

        // Step 5: Verify cascading updates

        // Verify nodes were updated
        final var currentNode1 = nodeRepository.findActive(node1.locator().id()).orElseThrow();
        final var currentNode2 = nodeRepository.findActive(node2.locator().id()).orElseThrow();

        assertEquals(2, currentNode1.locator().version(), "Node1 should be version 2");
        assertEquals(2, currentNode2.locator().version(), "Node2 should be version 2");

        // Verify nodes reference the new component version
        assertTrue(currentNode1.components().contains(updatedComp1.locator()));
        assertFalse(currentNode1.components().contains(comp1.locator()));

        assertTrue(currentNode2.components().contains(updatedComp1.locator()));
        assertTrue(currentNode2.components().contains(comp2.locator())); // Should still have comp2
        assertFalse(currentNode2.components().contains(comp1.locator()));

        // Verify edge was updated (both directly and due to node updates)
        final var edgeVersions = edgeRepository.findVersions(edge1.locator().id());
        assertTrue(edgeVersions.size() >= 2, "Edge should have multiple versions");

        final var currentEdge = edgeRepository.findActive(edge1.locator().id()).orElseThrow();
        assertTrue(currentEdge.components().contains(updatedComp1.locator()));
        assertFalse(currentEdge.components().contains(comp1.locator()));

        // Verify all timestamps are consistent
        assertEquals(updateTime, currentNode1.created());
        assertEquals(updateTime, currentNode2.created());
        assertEquals(updateTime, currentEdge.created());
    }

    @Test
    @DisplayName("Node expiry with integrity cascades to edges")
    void testNodeExpiryWithIntegrityCascade() {
        final var createTime = Instant.now();
        final var expireTime = createTime.plusSeconds(10);

        // Create nodes
        final var node1 = new SimpleNode(
                new Locator(NanoId.generate(), 1),
                defaultType,
                new TestData("Node1"),
                new HashSet<>(),
                createTime,
                Optional.empty());
        nodeRepository.save(node1);

        final var node2 = new SimpleNode(
                new Locator(NanoId.generate(), 1),
                defaultType,
                new TestData("Node2"),
                new HashSet<>(),
                createTime,
                Optional.empty());
        nodeRepository.save(node2);

        // Create edges
        final var edge1 = new SimpleEdge(
                new Locator(NanoId.generate(), 1),
                defaultType,
                node1,
                node2,
                new TestData("Edge1"),
                new HashSet<>(),
                createTime,
                Optional.empty());
        edgeRepository.save(edge1);

        final var edge2 = new SimpleEdge(
                new Locator(NanoId.generate(), 1),
                defaultType,
                node2,
                node1,
                new TestData("Edge2"),
                new HashSet<>(),
                createTime,
                Optional.empty());
        edgeRepository.save(edge2);

        // Expire node1 using repository with integrity
        nodeRepository.expire(node1.locator().id(), expireTime);

        // Verify node is expired
        assertTrue(nodeRepository.findActive(node1.locator().id()).isEmpty());

        // Verify edges are expired
        assertTrue(edgeRepository.findActive(edge1.locator().id()).isEmpty());
        assertTrue(edgeRepository.findActive(edge2.locator().id()).isEmpty());

        // Verify expired timestamps match
        final var expiredNode = nodeRepository.find(node1.locator());
        final var expiredEdge1 = edgeRepository.find(edge1.locator());
        final var expiredEdge2 = edgeRepository.find(edge2.locator());

        assertEquals(expireTime, expiredNode.expired().get());
        assertEquals(expireTime, expiredEdge1.expired().get());
        assertEquals(expireTime, expiredEdge2.expired().get());
    }

    @Test
    @DisplayName("Node update with integrity recreates edges")
    void testNodeUpdateWithIntegrityRecreatesEdges() {
        final var createTime = Instant.now();
        final var updateTime = createTime.plusSeconds(10);

        // Create component
        final var comp = new SimpleComponent(
                new Locator(NanoId.generate(), 1),
                componentType,
                new TestData("Component"),
                createTime,
                Optional.empty());
        componentRepository.save(comp);

        // Create nodes
        final var node1 = new SimpleNode(
                new Locator(NanoId.generate(), 1),
                defaultType,
                new TestData("Node1"),
                Set.of(comp.locator()),
                createTime,
                Optional.empty());
        nodeRepository.save(node1);

        final var node2 = new SimpleNode(
                new Locator(NanoId.generate(), 1),
                defaultType,
                new TestData("Node2"),
                new HashSet<>(),
                createTime,
                Optional.empty());
        nodeRepository.save(node2);

        // Create edge
        final var edge = new SimpleEdge(
                new Locator(NanoId.generate(), 1),
                defaultType,
                node1,
                node2,
                new TestData("Edge"),
                new HashSet<>(),
                createTime,
                Optional.empty());
        edgeRepository.save(edge);

        // Update node1
        final var updatedNode1 = new SimpleNode(
                new Locator(node1.locator().id(), 2),
                defaultType,
                new TestData("UpdatedNode1"),
                Set.of(comp.locator()),
                updateTime,
                Optional.empty());

        nodeRepository.update(node1, updatedNode1, updateTime);

        // Verify edge was recreated
        final var currentEdge = edgeRepository.findActive(edge.locator().id()).orElseThrow();
        assertEquals(2, currentEdge.locator().version());
        assertEquals(updatedNode1.locator(), currentEdge.source().locator());
        assertEquals(node2.locator(), currentEdge.target().locator());
        assertEquals(updateTime, currentEdge.created());
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
