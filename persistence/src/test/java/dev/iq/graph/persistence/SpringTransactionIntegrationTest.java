/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.persistence.config.TestPersistenceConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for Spring transaction handling across different persistence implementations.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SpringTransactionIntegrationTest.TestConfig.class)
@ActiveProfiles("sqlite") // Can be changed to "sqlite" or "mongodb"
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SpringTransactionIntegrationTest {

    @Configuration
    @EnableTransactionManagement
    @Import(TestPersistenceConfiguration.class)
    static class TestConfig {}

    @Inject
    private GraphRepository graphRepository;

    @Inject
    private TransactionTemplate transactionTemplate;

    @Test
    @Transactional
    public void testTransactionCommit() {

        final var nodeId = NanoId.generate();
        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "test-data");
        final var created = Instant.now();
        final var node = new SimpleNode(locator, List.of(), data, created, Optional.empty());

        // Save node within transaction
        graphRepository.nodes().save(node);

        // Verify node exists within same transaction
        final var foundNode = graphRepository.nodes().find(locator);
        assertTrue(foundNode.isPresent());
        assertEquals(data.value(), foundNode.get().data().value());
    }

    @Test
    public void testTransactionRollback() {

        final var nodeId = NanoId.generate();
        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "test-data");
        final var created = Instant.now();
        final var node = new SimpleNode(locator, List.of(), data, created, Optional.empty());

        // Save node in transaction but rollback
        assertThrows(RuntimeException.class, () -> {
            transactionTemplate.execute(status -> {
                graphRepository.nodes().save(node);
                // Force rollback
                throw new RuntimeException("Simulated error");
            });
        });

        // Verify node does not exist after rollback
        // Need to check within a transaction to ensure proper isolation
        final var nodeExists = transactionTemplate.execute(status -> {
            final var foundNode = graphRepository.nodes().find(locator);
            return foundNode.isPresent();
        });
        assertFalse(nodeExists);
    }

    @Test
    @Disabled("SQLite in-memory DB with shared cache mode does not provide proper transaction isolation. "
            + "The shared cache allows all connections to see uncommitted changes, breaking ACID isolation. "
            + "This would work with a file-based SQLite DB or other databases like PostgreSQL.")
    public void testTransactionIsolation() {

        final var nodeId = NanoId.generate();
        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "test-data");
        final var created = Instant.now();
        final var node = new SimpleNode(locator, List.of(), data, created, Optional.empty());

        // Start transaction but don't commit yet
        transactionTemplate.execute(status -> {
            graphRepository.nodes().save(node);

            // Within transaction, node should be visible
            final var foundNode = graphRepository.nodes().find(locator);
            assertTrue(foundNode.isPresent());

            // Mark for rollback to test isolation
            status.setRollbackOnly();
            return null;
        });

        // After rollback, node should not exist
        // Need to check within a transaction to ensure proper isolation
        final var nodeExists = transactionTemplate.execute(status -> {
            final var foundNode = graphRepository.nodes().find(locator);
            return foundNode.isPresent();
        });
        assertFalse(nodeExists);
    }
}
