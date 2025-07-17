/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.tinkerpop;

import org.junit.jupiter.api.DisplayName;

import dev.iq.graph.persistence.AbstractGraphListenerReferentialIntegrityIntegrationTest;
import dev.iq.graph.persistence.GraphRepository;

/**
 * Integration tests for TinkerpopGraphRepository to validate
 * referential integrity when using graph listeners.
 */
@DisplayName("Tinkerpop Graph Listener Referential Integrity Integration Tests")
final class TinkerpopGraphListenerReferentialIntegrityIntegrationTest
        extends AbstractGraphListenerReferentialIntegrityIntegrationTest {

    @Override
    protected GraphRepository createGraphRepository() {

        final var factory = new TinkerpopSessionFactory();
        try (final var session = (TinkerpopSession) factory.create()) {
            return TinkerpopGraphRepository.create(session);
        }
    }
}
