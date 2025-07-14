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
        return toPath(jgraphtPath);
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
            final var path = toPath(jgraphtPath);
            if (!containsCycle(path)) {
                result.add(path);
            }
        }
        return result;
    }

    /**
     * Converts from a JGraphT GraphPath to a Path representation.
     */
    private static Path toPath(final GraphPath<Node, Edge> jgraphtPath) {

        final var vertices = jgraphtPath.getVertexList();
        final var edges = jgraphtPath.getEdgeList();
        if (vertices.size() != (edges.size() + 1)) {
            throw new IllegalArgumentException("Vertex count must be one greater than edge count");
        }
        final List<Element> elements = new ArrayList<>();
        for (var i = 0; i < vertices.size(); i++) {
            elements.add(vertices.get(i));
            if (i < edges.size()) {
                elements.add(edges.get(i));
            }
        }
        return new Path(elements);
    }

    /**
     * Checks if a path contains a cycle (revisits the same node).
     * Note: This is different from general cycle detection - we're checking if a specific path
     * revisits any node, which makes it invalid as a simple path.
     */
    private static boolean containsCycle(final Path path) {

        final var visitedNodes = new HashSet<Node>();
        for (final var element : path.elements()) {
            if (element instanceof final Node node) {
                if (visitedNodes.contains(node)) {
                    return true;
                }
                visitedNodes.add(node);
            }
        }
        return false;
    }
}
