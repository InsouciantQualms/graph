/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.VersionedFinder;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Path;
import dev.iq.graph.model.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;

/**
 * JGraphT implementation of View that provides a filtered view of a graph.
 */
public final class JGraphtGraphView implements View {

    /** Delegate graph. */
    private final Graph<Node, Edge> graph;

    /** Creates a view (graph or component) using the specified delegate graph. */
    public JGraphtGraphView(final Graph<Node, Edge> graph) {

        this.graph = graph;
    }

    /** {@inheritDoc} */
    @Override
    public VersionedFinder<Node> nodes() {

        return new JGraphtNodeFinder(graph);
    }

    /** {@inheritDoc} */
    @Override
    public VersionedFinder<Edge> edges() {

        return new JGraphtEdgeFinder(graph);
    }

    /** {@inheritDoc} */
    @Override
    public Set<Edge> outgoingEdges(final Node node) {

        return Set.copyOf(graph.outgoingEdgesOf(node));
    }

    /** {@inheritDoc} */
    @Override
    public Set<Edge> incomingEdges(final Node node) {

        return Set.copyOf(graph.incomingEdgesOf(node));
    }

    /** {@inheritDoc} */
    @Override
    public Set<Node> neighbors(final Node node) {

        final var outgoingNeighbors = graph.outgoingEdgesOf(node).stream()
                .filter(edge -> edge.expired().isEmpty())
                .map(Edge::target);

        final var incomingNeighbors = graph.incomingEdgesOf(node).stream()
                .filter(edge -> edge.expired().isEmpty())
                .map(Edge::source);

        return Stream.concat(outgoingNeighbors, incomingNeighbors).collect(Collectors.toSet());
    }

    /** {@inheritDoc} */
    @Override
    public boolean pathExists(final Node source, final Node target) {

        final var inspector = new ConnectivityInspector<>(graph);
        return inspector.pathExists(source, target);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Path> findShortestPath(final Node source, final Node target) {

        final var pathAlgorithm = new DijkstraShortestPath<>(graph);
        final var jgraphtPath = pathAlgorithm.getPath(source, target);
        if (jgraphtPath == null) {
            return Optional.empty();
        }
        return Optional.of(toPath(jgraphtPath));
    }

    /** {@inheritDoc} */
    @Override
    public List<Path> findAllPaths(final Node source, final Node target) {

        final var allPathsAlgorithm = new AllDirectedPaths<>(graph);
        final var maxPathLength = graph.vertexSet().size();
        final var jgraphtPaths = allPathsAlgorithm.getAllPaths(source, target, true, maxPathLength);
        return jgraphtPaths.stream()
                .map(JGraphtGraphView::toPath)
                .filter(path -> !path.containsCycle())
                .toList();
    }

    /** Converts a JGraphT GraphPath to our Path model. */
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
}
