package dev.iq.graph.model.jgrapht;

import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Path;
import java.util.List;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;

/**
 * Operations that derive or compute paths between nodes.
 */
public final class PathOperations {

    /** Delegate graph. */
    private final Graph<Node, Edge> graph;

    /** Creates a new path operations delegating to the underlying graph. */
    public PathOperations(final Graph<Node, Edge> graph) {

        this.graph = graph;
    }

    /** Finds shortest path between two nodes. */
    public Path shortestPath(final Node source, final Node target) {

        final var pathAlgorithm = new DijkstraShortestPath<>(graph);
        final var jgraphtPath = pathAlgorithm.getPath(source, target);
        if (jgraphtPath == null) {
            return new Path(List.of());
        }
        return OperationsHelper.toPath(jgraphtPath);
    }

    /** Checks if path exists between two nodes. */
    public boolean pathExists(final Node source, final Node target) {

        final var inspector = new ConnectivityInspector<>(graph);
        return inspector.pathExists(source, target);
    }

    /** Returns all possible paths between two nodes. */
    public List<Path> allPaths(final Node source, final Node target) {

        final var allPathsAlgorithm = new AllDirectedPaths<>(graph);
        final var maxPathLength = graph.vertexSet().size();
        final var jgraphtPaths = allPathsAlgorithm.getAllPaths(source, target, true, maxPathLength);
        return jgraphtPaths.stream()
                .map(OperationsHelper::toPath)
                .filter(path -> !OperationsHelper.containsCycle(path))
                .toList();
    }
}
