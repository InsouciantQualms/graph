/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api.impl;

import dev.iq.common.version.NanoId;
import dev.iq.graph.api.GraphService;
import dev.iq.graph.model.Path;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of GraphService using session-based transactions.
 */
@Service
@Transactional
public final class DefaultGraphService implements GraphService {

    public DefaultGraphService() {}

    @Override
    public boolean hasPath(final NanoId sourceNodeId, final NanoId targetNodeId) {

        // TODO: This requires integration with GraphOperations and building in-memory graph from repository
        // For now, returning false as a placeholder
        return false;
    }

    @Override
    public List<Path> getActiveConnected() {

        // TODO: This requires integration with GraphOperations and building in-memory graph from repository
        // For now, returning empty list as a placeholder
        return List.of();
    }

    @Override
    public Path getShortestPath(final NanoId sourceNodeId, final NanoId targetNodeId) {

        // TODO: This requires integration with GraphOperations and building in-memory graph from repository
        throw new UnsupportedOperationException(
                "Not yet implemented - requires integration with GraphOperations and repository data");
    }
}
