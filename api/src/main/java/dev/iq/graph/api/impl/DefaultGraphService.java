/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api.impl;

import dev.iq.common.version.NanoId;
import dev.iq.graph.api.GraphService;
import dev.iq.graph.model.GraphSpace;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Path;
import dev.iq.graph.model.View;
import dev.iq.graph.model.jgrapht.JGraphtGraphBuilder;
import dev.iq.graph.persistence.GraphRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of GraphService that delegates to persistence layer and model operations.
 */
public final class DefaultGraphService implements GraphService {

    private final GraphRepository graphRepository;

    public DefaultGraphService(final GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    @Override
    public boolean hasPath(final NanoId sourceNodeId, final NanoId targetNodeId) {
        final GraphSpace graphSpace = buildGraphSpace();
        final View view = graphSpace.view();

        final Optional<Node> sourceNode = view.nodes().findActive(sourceNodeId);
        final Optional<Node> targetNode = view.nodes().findActive(targetNodeId);

        if (sourceNode.isEmpty() || targetNode.isEmpty()) {
            return false;
        }

        return view.pathExists(sourceNode.get(), targetNode.get());
    }

    @Override
    public List<Path> getActiveConnected() {
        final GraphSpace graphSpace = buildGraphSpace();
        final View view = graphSpace.view();

        final List<Path> connectedPaths = new ArrayList<>();
        final List<NanoId> activeNodeIds = graphRepository.nodes().allActiveIds();

        // For each pair of nodes, check if they're connected and add the path
        for (int i = 0; i < activeNodeIds.size(); i++) {
            for (int j = i + 1; j < activeNodeIds.size(); j++) {
                final Optional<Node> sourceNode = view.nodes().findActive(activeNodeIds.get(i));
                final Optional<Node> targetNode = view.nodes().findActive(activeNodeIds.get(j));

                if (sourceNode.isPresent() && targetNode.isPresent()) {
                    final Optional<Path> path = view.findShortestPath(sourceNode.get(), targetNode.get());
                    path.ifPresent(connectedPaths::add);
                }
            }
        }

        return connectedPaths;
    }

    @Override
    public Path getShortestPath(final NanoId sourceNodeId, final NanoId targetNodeId) {
        final GraphSpace graphSpace = buildGraphSpace();
        final View view = graphSpace.view();

        final Optional<Node> sourceNode = view.nodes().findActive(sourceNodeId);
        final Optional<Node> targetNode = view.nodes().findActive(targetNodeId);

        if (sourceNode.isEmpty() || targetNode.isEmpty()) {
            throw new IllegalArgumentException("Source or target node not found");
        }

        return view.findShortestPath(sourceNode.get(), targetNode.get())
                .orElseThrow(() -> new IllegalStateException("No path found between nodes"));
    }

    private GraphSpace buildGraphSpace() {
        // Build the graph from persistence layer
        final JGraphtGraphBuilder builder = new JGraphtGraphBuilder();

        // Load all active nodes
        graphRepository.nodes().allActiveIds().forEach(nodeId -> {
            graphRepository.nodes().findActive(nodeId).ifPresent(node -> {
                builder.addNode(node.locator().id(), node.type(), node.data(), node.created());
            });
        });

        // Load all active edges
        graphRepository.edges().allActiveIds().forEach(edgeId -> {
            graphRepository.edges().findActive(edgeId).ifPresent(edge -> {
                builder.addEdge(
                        edge.type(), edge.source(), edge.target(), edge.data(), edge.components(), edge.created());
            });
        });

        return builder.build();
    }
}
