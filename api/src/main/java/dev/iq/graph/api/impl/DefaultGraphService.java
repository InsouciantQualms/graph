/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api.impl;

import dev.iq.common.persist.SessionFactory;
import dev.iq.common.version.NanoId;
import dev.iq.graph.api.GraphService;
import dev.iq.graph.model.Path;

import java.util.List;

/**
 * Default implementation of GraphService using session-based transactions.
 */
public final class DefaultGraphService implements GraphService {

    private final SessionFactory sessionFactory;

    public DefaultGraphService(final SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public boolean hasPath(final NanoId sourceNodeId, final NanoId targetNodeId) {
        try (final var session = sessionFactory.create()) {
            // TODO: This requires integration with GraphOperations and building in-memory graph from repository
            // For now, returning false as a placeholder
            return false;
        }
    }

    @Override
    public List<Path> getActiveConnected() {
        try (final var session = sessionFactory.create()) {
            // TODO: This requires integration with GraphOperations and building in-memory graph from repository
            // For now, returning empty list as a placeholder
            return List.of();
        }
    }

    @Override
    public Path getShortestPath(final NanoId sourceNodeId, final NanoId targetNodeId) {
        try (final var session = sessionFactory.create()) {
            // TODO: This requires integration with GraphOperations and building in-memory graph from repository
            throw new UnsupportedOperationException("Not yet implemented - requires integration with GraphOperations and repository data");
        }
    }
}