/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Path;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.ListenableGraph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.event.GraphListener;
import org.jgrapht.graph.DefaultListenableGraph;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.graph.GraphWalk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Operations that apply to the entire graph.
 */
public final class GraphOperations {

    /** JGraphT delegated in memory graph. */
    private final Graph<Node, Edge> graph;

    /**
     * Creates graph operations that will forward any events on to the specified listener
     */
    public GraphOperations(final GraphListener<Node, Edge> listener) {

        final Graph<Node, Edge> base = new DirectedMultigraph<>(null, null, false);
        final ListenableGraph<Node, Edge> wrapper = new DefaultListenableGraph<>(base);
        wrapper.addGraphListener(listener);
        graph = wrapper;
    }

    /**
     * Finds shortest path between two nodes.
     */
    public Path shortestPath(final Node source, final Node target) {

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
    public boolean pathExists(final Node source, final Node target) {

        final var inspector = new ConnectivityInspector<>(graph);
        return inspector.pathExists(source, target);
    }

    public List<Path> allPaths(final Node source, final Node target) {

        final var allPathsAlgorithm = new AllDirectedPaths<>(graph);
        final var maxPathLength = graph.vertexSet().size();
        final var jgraphtPaths = allPathsAlgorithm.getAllPaths(source, target, true, maxPathLength);
        final var result = new ArrayList<Path>();
        
        for (final var jgraphtPath : jgraphtPaths) {
            // Create a subgraph from the path to check for cycles
            final var pathVertices = new HashSet<>(jgraphtPath.getVertexList());
            final var pathEdges = new HashSet<>(jgraphtPath.getEdgeList());
            
            // Check if this specific path contains a cycle by checking if it revisits any vertex
            final var path = OperationsHelper.toPath(jgraphtPath);
            if (!OperationsHelper.containsCycle(path)) {
                result.add(path);
            }
        }
        return result;
    }

    /**
     * Converts from a JGraphT GraphPath to a Path representation.
     */
}
