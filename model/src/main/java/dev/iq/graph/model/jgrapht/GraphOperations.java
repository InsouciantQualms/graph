/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Path;
import dev.iq.graph.model.Reference;
import java.util.List;
import org.jgrapht.Graph;
import org.jgrapht.ListenableGraph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.event.GraphListener;
import org.jgrapht.graph.DefaultListenableGraph;
import org.jgrapht.graph.DirectedMultigraph;

/**
 * Operations that apply to the entire graph.
 */
public final class GraphOperations {

    /** JGraphT delegated in memory graph. */
    private final Graph<Reference<Node>, Reference<Edge>> graph;

    /**
     * Creates graph operations that will forward any events on to the specified listener.
     */
    public GraphOperations(final GraphListener<Reference<Node>, Reference<Edge>> listener) {

        final Graph<Reference<Node>, Reference<Edge>> base = new DirectedMultigraph<>(null, null, false);
        final ListenableGraph<Reference<Node>, Reference<Edge>> wrapper = new DefaultListenableGraph<>(base);
        wrapper.addGraphListener(listener);
        graph = wrapper;
    }

    /**
     * Finds shortest path between two nodes.
     */
    public Path shortestPath(final Reference<Node> source, final Reference<Node> target) {

        final var pathAlgorithm = new DijkstraShortestPath<>(graph);
        final var jgraphtPath = pathAlgorithm.getPath(source, target);
        if (jgraphtPath == null) {
            return new Path(List.of());
        }
        return OperationsHelper.toPath(jgraphtPath);
    }

    /**
     * Checks if path exists between two nodes.
     */
    public boolean pathExists(final Reference<Node> source, final Reference<Node> target) {

        final var inspector = new ConnectivityInspector<>(graph);
        return inspector.pathExists(source, target);
    }

    public List<Path> allPaths(final Reference<Node> source, final Reference<Node> target) {

        final var allPathsAlgorithm = new AllDirectedPaths<>(graph);
        final var maxPathLength = graph.vertexSet().size();
        final var jgraphtPaths = allPathsAlgorithm.getAllPaths(source, target, true, maxPathLength);
        return jgraphtPaths.stream()
                .map(OperationsHelper::toPath)
                .filter(path -> !OperationsHelper.containsCycle(path))
                .toList();
    }
}
