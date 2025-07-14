package dev.iq.graph.persistence.tinkerpop;

import dev.iq.graph.persistence.AbstractGraphListenerReferentialIntegrityIntegrationTest;
import dev.iq.graph.persistence.GraphRepository;
import org.junit.jupiter.api.DisplayName;

/**
 * Integration tests for TinkerpopGraphRepository to validate
 * referential integrity when using graph listeners.
 */
@DisplayName("Tinkerpop Graph Listener Referential Integrity Integration Tests")
final class TinkerpopGraphListenerReferentialIntegrityIntegrationTest extends AbstractGraphListenerReferentialIntegrityIntegrationTest {

    @Override
    protected GraphRepository createGraphRepository() {

        final var factory = new TinkerpopSessionFactory();
        try (final var session = (TinkerpopSession) factory.create()) {
            return TinkerpopGraphRepository.create(session);
        }
    }
}