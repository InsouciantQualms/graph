/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for SqliteSession transaction handling.
 * @deprecated This test is replaced by SpringTransactionIntegrationTest which uses Spring transactions
 */
@Deprecated
final class SqliteSessionIntegrationTest {

    private static DataSource dataSource;
    private SqliteSessionFactory sessionFactory;

    @BeforeAll
    static void beforeClass() {
        dataSource = SqlliteTestConnectionHelper.getSharedDataSource();
    }

    @AfterAll
    static void afterClass() {
        SqlliteTestConnectionHelper.closeSharedDataSource();
    }

    @BeforeEach
    void before() {
        sessionFactory = new SqliteSessionFactory(dataSource);
    }

    @Test
    void testSessionCommit() {
        final var nodeId = NanoId.generate();
        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "test-data");
        final var created = Instant.now();
        final var node =
                new SimpleNode(locator, new SimpleType("test"), data, new HashSet<>(), created, Optional.empty());

        // Save node in session and commit manually
        try (var session = sessionFactory.create()) {
            final var repository = SqliteGraphRepository.create((SqliteHandleProvider) session);
            repository.nodes().save(node);
            session.commit();
        }

        // Verify node exists in new session
        try (var session = sessionFactory.create()) {
            final var repository = SqliteGraphRepository.create((SqliteHandleProvider) session);
            final var foundNode = repository.nodes().find(locator);
            assertNotNull(foundNode);
            assertEquals(data.value(), foundNode.data().value());
        }
    }

    @Test
    void testSessionRollback() {
        final var nodeId = NanoId.generate();
        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "test-data");
        final var created = Instant.now();
        final var node =
                new SimpleNode(locator, new SimpleType("test"), data, new HashSet<>(), created, Optional.empty());

        // Save node in session but rollback manually
        try (var session = sessionFactory.create()) {
            final var repository = SqliteGraphRepository.create((SqliteHandleProvider) session);
            repository.nodes().save(node);
            session.rollback();
        }

        // Verify node does not exist in new session
        try (var session = sessionFactory.create()) {
            final var repository = SqliteGraphRepository.create((SqliteHandleProvider) session);
            assertThrows(
                    dev.iq.common.error.IoException.class,
                    () -> repository.nodes().find(locator));
        }
    }

    @Test
    void testAutoRollbackOnException() {
        final var nodeId = NanoId.generate();
        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "test-data");
        final var created = Instant.now();
        final var node =
                new SimpleNode(locator, new SimpleType("test"), data, new HashSet<>(), created, Optional.empty());

        // Save node in session but cause exception without explicit commit/rollback
        assertThrows(RuntimeException.class, () -> {
            try (var session = sessionFactory.create()) {
                final var repository = SqliteGraphRepository.create((SqliteHandleProvider) session);
                repository.nodes().save(node);
                // Simulate an error before commit - session should auto-rollback on close
                throw new RuntimeException("Simulated error");
            }
        });

        // Verify node does not exist in new session (auto-rollback occurred)
        try (var session = sessionFactory.create()) {
            final var repository = SqliteGraphRepository.create((SqliteHandleProvider) session);
            assertThrows(
                    dev.iq.common.error.IoException.class,
                    () -> repository.nodes().find(locator));
        }
    }
}
