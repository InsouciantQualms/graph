/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.model.simple.SimpleNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Unit tests for MongoNodeRepository using embedded MongoDB.
 */
@Timeout(30) // 30 second timeout for all tests
final class MongoNodeRepositoryTest {

    private static TransitionWalker.ReachedState<RunningMongodProcess> mongodProcess;
    private static MongoClient mongoClient;
    private static MongoDatabase database;
    private MongoNodeRepository repository;

    @BeforeAll
    static void setUpClass() {
        mongodProcess = Mongod.instance().start(Version.Main.V7_0);
        final var serverAddress = mongodProcess.current().getServerAddress();

        mongoClient = MongoClients.create("mongodb://" + serverAddress.getHost() + ':' + serverAddress.getPort());
        database = mongoClient.getDatabase("test_graph");
    }

    @AfterAll
    static void tearDownClass() {
        try {
            if (mongoClient != null) {
                mongoClient.close();
            }
        } finally {
            if (mongodProcess != null) {
                mongodProcess.close();
            }
        }

        // Give time for processes to shut down
        try {
            Thread.sleep(1000);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @BeforeEach
    void before() {
        database.drop();
        repository = new MongoNodeRepository(database);
    }

    @Test
    void testSaveAndFind() {
        final var nodeId = new NanoId("test-node");
        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "test-value");
        final var created = Instant.now();
        final var node = new SimpleNode(locator, List.of(), data, created, Optional.empty());

        final var savedNode = repository.save(node);
        assertEquals(node, savedNode);

        final var foundNode = repository.find(locator);
        assertTrue(foundNode.isPresent());
        assertEquals(locator, foundNode.get().locator());
        assertSame(data.type(), foundNode.get().data().type());
        assertEquals(data.value(), foundNode.get().data().value());
    }

    @Test
    void testFindActive() {
        final var nodeId = new NanoId("active-node");
        final var locator1 = new Locator(nodeId, 1);
        final var locator2 = new Locator(nodeId, 2);
        final var data = new SimpleData(String.class, "test-value");
        final var created = Instant.now();

        final var node1 = new SimpleNode(locator1, List.of(), data, created, Optional.of(created.plusSeconds(10)));
        final var node2 = new SimpleNode(locator2, List.of(), data, created.plusSeconds(5), Optional.empty());

        repository.save(node1);
        repository.save(node2);

        final var activeNode = repository.findActive(nodeId);
        assertTrue(activeNode.isPresent());
        assertEquals(locator2, activeNode.get().locator());
    }

    @Test
    void testFindAll() {
        final var nodeId = new NanoId("versioned-node");
        final var data = new SimpleData(String.class, "test-value");
        final var created = Instant.now();

        final var node1 = new SimpleNode(new Locator(nodeId, 1), List.of(), data, created, Optional.empty());
        final var node2 = new SimpleNode(new Locator(nodeId, 2), List.of(), data, created, Optional.empty());

        repository.save(node1);
        repository.save(node2);

        final var allNodes = repository.findAll(nodeId);
        assertEquals(2, allNodes.size());
        assertEquals(1, allNodes.get(0).locator().version());
        assertEquals(2, allNodes.get(1).locator().version());
    }

    @Test
    void testExpire() {
        final var nodeId = new NanoId("expire-node");
        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "test-value");
        final var created = Instant.now();
        final var node = new SimpleNode(locator, List.of(), data, created, Optional.empty());

        repository.save(node);

        final var expiredAt = Instant.now();
        assertTrue(repository.expire(nodeId, expiredAt));

        final var foundNode = repository.findActive(nodeId);
        assertFalse(foundNode.isPresent());
    }

    @Test
    void testDelete() {
        final var nodeId = new NanoId("delete-node");
        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "test-value");
        final var created = Instant.now();
        final var node = new SimpleNode(locator, List.of(), data, created, Optional.empty());

        repository.save(node);
        assertTrue(repository.delete(nodeId));

        final var foundNode = repository.find(locator);
        assertFalse(foundNode.isPresent());
    }
}
