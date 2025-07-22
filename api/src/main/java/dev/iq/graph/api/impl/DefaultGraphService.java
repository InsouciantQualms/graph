/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api.impl;

import dev.iq.common.version.NanoId;
import dev.iq.graph.api.GraphService;
import dev.iq.graph.model.Path;
import dev.iq.graph.model.jgrapht.GraphOperations;
import dev.iq.graph.model.jgrapht.NodeOperations;
import dev.iq.graph.persistence.GraphRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of GraphService using session-based transactions.
 */
@Service
public final class DefaultGraphService implements GraphService {

    private final GraphRepository repository;
    private final GraphOperations graphOperations;
    private final NodeOperations nodeOperations;

    public DefaultGraphService(
            final GraphRepository repository,
            final GraphOperations graphOperations,
            final NodeOperations nodeOperations) {

        this.repository = repository;
        this.graphOperations = graphOperations;
        this.nodeOperations = nodeOperations;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPath(final NanoId sourceNodeId, final NanoId targetNodeId) {

        final var sourceNode = nodeOperations
                .findActive(sourceNodeId)
                .orElseThrow(() -> new IllegalArgumentException("Source node not found: " + sourceNodeId));
        final var targetNode = nodeOperations
                .findActive(targetNodeId)
                .orElseThrow(() -> new IllegalArgumentException("Target node not found: " + targetNodeId));

        return graphOperations.pathExists(sourceNode, targetNode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Path> getActiveConnected() {

        // Get all active nodes
        final var activeNodes = nodeOperations.allActive();

        // Find all connected paths among active nodes
        final var connectedPaths = new java.util.ArrayList<Path>();

        for (var i = 0; i < activeNodes.size(); i++) {
            for (var j = i + 1; j < activeNodes.size(); j++) {
                final var sourceNode = activeNodes.get(i);
                final var targetNode = activeNodes.get(j);

                if (graphOperations.pathExists(sourceNode, targetNode)) {
                    final var paths = graphOperations.allPaths(sourceNode, targetNode);
                    connectedPaths.addAll(paths);
                }
            }
        }

        return connectedPaths;
    }

    @Override
    @Transactional(readOnly = true)
    public Path getShortestPath(final NanoId sourceNodeId, final NanoId targetNodeId) {

        final var sourceNode = nodeOperations
                .findActive(sourceNodeId)
                .orElseThrow(() -> new IllegalArgumentException("Source node not found: " + sourceNodeId));
        final var targetNode = nodeOperations
                .findActive(targetNodeId)
                .orElseThrow(() -> new IllegalArgumentException("Target node not found: " + targetNodeId));

        return graphOperations.shortestPath(sourceNode, targetNode);
    }
}
