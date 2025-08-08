/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Path;
import java.util.List;
import java.util.Optional;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;

/**
 * Operations that derive or compute paths between nodes.
 */
public final class JGraphtPathOperations {

    /** Delegate graph. */
    private final Graph<Node, Edge> graph;

    /** Creates a new path operations delegating to the underlying graph. */
    public JGraphtPathOperations(final Graph<Node, Edge> graph) {

        this.graph = graph;
    }

    @Override
    public Optional<Path> findShortestPath(final Node source, final Node target) {

        final var pathAlgorithm = new DijkstraShortestPath<>(graph);
        final var jgraphtPath = pathAlgorithm.getPath(source, target);
        if (jgraphtPath == null) {
            return Optional.empty();
        }
        return Optional.of(JGraphtOperationsHelper.toPath(jgraphtPath));
    }

    @Override
    public boolean pathExists(final Node source, final Node target) {

        final var inspector = new ConnectivityInspector<>(graph);
        return inspector.pathExists(source, target);
    }

    @Override
    public List<Path> findAllPaths(final Node source, final Node target) {

        final var allPathsAlgorithm = new AllDirectedPaths<>(graph);
        final var maxPathLength = graph.vertexSet().size();
        final var jgraphtPaths = allPathsAlgorithm.getAllPaths(source, target, true, maxPathLength);
        return jgraphtPaths.stream()
                .map(JGraphtOperationsHelper::toPath)
                .filter(path -> !JGraphtOperationsHelper.containsCycle(path))
                .toList();
    }
}
