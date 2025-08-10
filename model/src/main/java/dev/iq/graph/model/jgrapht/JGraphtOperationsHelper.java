/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Uid;
import dev.iq.common.version.Versioned;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.AsSubgraph;

/**
 * Helper utilities for graph operations.
 */
public final class JGraphtOperationsHelper {

    /**
     * Type contains only static members.
     */
    private JGraphtOperationsHelper() {}

    /**
     * Validates that an element can be expired.
     */
    public static <E extends Versioned> E validateForExpiry(
            final Optional<E> element, final Uid id, final String elementType) {

        return element.orElseThrow(() -> new IllegalArgumentException(elementType + " not found: " + id));
    }

    /**
     * Converts a JGraphT GraphPath to our Path model.
     */
    public static Path toPath(final GraphPath<Node, Edge> jgraphtPath) {

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
     */
    public static boolean containsCycle(final Path path) {

        final var visitedNodes = new HashSet<Node>();
        return path.elements().stream()
                .filter(element -> element instanceof Node)
                .map(element -> (Node) element)
                .anyMatch(node -> !visitedNodes.add(node));
    }

    /**
     * Validates component elements according to component constraints.
     */
    public static void validateComponentElements(final Collection<Element> elements, final Graph<Node, Edge> graph) {

        if (elements.isEmpty()) {
            throw new IllegalArgumentException("Component must contain at least one element");
        }

        final var nodes = new HashSet<Node>();
        final var edges = new HashSet<Edge>();

        elements.forEach(element -> {
            switch (element) {
                case final Node node -> nodes.add(node);
                case final Edge edge -> edges.add(edge);
                default -> throw new IllegalArgumentException(
                        "Unknown element type: " + element.getClass().getSimpleName());
            }
        });

        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Component must contain at least one node");
        }

        validateConnectivity(nodes, edges, graph);
        validateNoCycles(nodes, edges, graph);
        validateLeafNodesOnly(nodes, edges);
    }

    /**
     * Validates that all elements in a component are connected.
     */
    private static void validateConnectivity(
            final Collection<Node> nodes, final Collection<Edge> edges, final Graph<Node, Edge> graph) {

        if ((nodes.size() == 1) && edges.isEmpty()) {
            return;
        }

        // Create a subgraph containing only the component's nodes and edges
        final var subgraph = new AsSubgraph<Node, Edge>(graph, new HashSet<>(nodes), new HashSet<>(edges));

        // Use ConnectivityInspector to check if all nodes are connected
        final var inspector = new ConnectivityInspector<>(subgraph);
        if (!inspector.isConnected()) {
            throw new IllegalArgumentException("All elements in a component must be connected");
        }
    }

    /**
     * Validates that a component contains no cycles.
     */
    private static void validateNoCycles(
            final Collection<Node> nodes, final Collection<Edge> edges, final Graph<Node, Edge> graph) {

        // Create a subgraph containing only the component's nodes and edges
        final var subgraph = new AsSubgraph<Node, Edge>(graph, new HashSet<>(nodes), new HashSet<>(edges));

        // Use JGraphT's CycleDetector to check for cycles
        final var cycleDetector = new CycleDetector<>(subgraph);
        if (cycleDetector.detectCycles()) {
            throw new IllegalArgumentException("Components cannot contain cycles");
        }
    }

    /**
     * Validates that leaf elements are nodes only.
     */
    private static void validateLeafNodesOnly(final Collection<Node> nodes, final Iterable<Edge> edges) {

        edges.forEach(edge -> {
            // Use the edge's source and target directly instead of querying the graph
            final var source = edge.source();
            final var target = edge.target();

            if (!nodes.contains(source) || !nodes.contains(target)) {
                throw new IllegalArgumentException("All edges in a component must connect nodes within the component");
            }
        });
    }
}
