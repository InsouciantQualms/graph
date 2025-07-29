/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.NanoId;
import dev.iq.common.version.Versioned;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Path;
import dev.iq.graph.model.Reference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.AsSubgraph;

/**
 * Helper utilities for graph operations.
 */
public final class OperationsHelper {

    /**
     * Type contains only static members.
     */
    private OperationsHelper() {}

    /**
     * Validates that an element can be expired.
     */
    public static <E extends Versioned> E validateForExpiry(
            final Optional<E> element, final NanoId id, final String elementType) {

        return element.orElseThrow(() -> new IllegalArgumentException(elementType + " not found: " + id));
    }

    /**
     * Converts a JGraphT GraphPath to our Path model.
     */
    public static Path toPath(final GraphPath<Reference<Node>, Reference<Edge>> jgraphtPath) {

        final var vertices = jgraphtPath.getVertexList();
        final var edges = jgraphtPath.getEdgeList();
        if (vertices.size() != (edges.size() + 1)) {
            throw new IllegalArgumentException("Vertex count must be one greater than edge count");
        }
        final List<Reference<Element>> elements = new ArrayList<>();
        for (var i = 0; i < vertices.size(); i++) {
            // Vertices are Reference<Node>, need to convert to Reference<Element>
            final Reference<Node> nodeRef = vertices.get(i);
            if (nodeRef instanceof Reference.Loaded<Node> loaded) {
                elements.add(new Reference.Loaded<>(loaded.value()));
            } else if (nodeRef instanceof Reference.Unloaded<Node> unloaded) {
                elements.add(new Reference.Unloaded<>(unloaded.locator(), Element.class));
            }
            if (i < edges.size()) {
                // Edges are Reference<Edge>, need to convert to Reference<Element>
                final Reference<Edge> edgeRef = edges.get(i);
                if (edgeRef instanceof Reference.Loaded<Edge> loaded) {
                    elements.add(new Reference.Loaded<>(loaded.value()));
                } else if (edgeRef instanceof Reference.Unloaded<Edge> unloaded) {
                    elements.add(new Reference.Unloaded<>(unloaded.locator(), Element.class));
                }
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
                .filter(ref -> ref instanceof Reference.Loaded<Element> loaded && loaded.value() instanceof Node)
                .map(ref -> (Node) ((Reference.Loaded<Element>) ref).value())
                .anyMatch(node -> !visitedNodes.add(node));
    }

    /**
     * Validates component elements according to component constraints.
     */
    public static void validateComponentElements(
            final Collection<Element> elements, final Graph<Reference<Node>, Reference<Edge>> graph) {

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
            final Set<Node> nodes, final Set<Edge> edges, final Graph<Reference<Node>, Reference<Edge>> graph) {

        if ((nodes.size() == 1) && edges.isEmpty()) {
            return;
        }

        // Convert nodes and edges to their references for the subgraph
        final Set<Reference<Node>> nodeRefs = nodes.stream()
                .map(node -> (Reference<Node>) new Reference.Loaded<>(node))
                .collect(HashSet::new, Set::add, Set::addAll);
        final Set<Reference<Edge>> edgeRefs = edges.stream()
                .map(edge -> (Reference<Edge>) new Reference.Loaded<>(edge))
                .collect(HashSet::new, Set::add, Set::addAll);

        // Create a subgraph containing only the component's nodes and edges
        final var subgraph = new AsSubgraph<>(graph, nodeRefs, edgeRefs);

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
            final Set<Node> nodes, final Set<Edge> edges, final Graph<Reference<Node>, Reference<Edge>> graph) {

        // Convert nodes and edges to their references for the subgraph
        final Set<Reference<Node>> nodeRefs = nodes.stream()
                .map(node -> (Reference<Node>) new Reference.Loaded<>(node))
                .collect(HashSet::new, Set::add, Set::addAll);
        final Set<Reference<Edge>> edgeRefs = edges.stream()
                .map(edge -> (Reference<Edge>) new Reference.Loaded<>(edge))
                .collect(HashSet::new, Set::add, Set::addAll);

        // Create a subgraph containing only the component's nodes and edges
        final var subgraph = new AsSubgraph<>(graph, nodeRefs, edgeRefs);

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
            final var sourceRef = edge.source();
            final var targetRef = edge.target();

            // Extract the actual nodes from the references
            final Node sourceNode = sourceRef instanceof Reference.Loaded<Node> loaded ? loaded.value() : null;
            final Node targetNode = targetRef instanceof Reference.Loaded<Node> loaded ? loaded.value() : null;

            if (sourceNode == null
                    || targetNode == null
                    || !nodes.contains(sourceNode)
                    || !nodes.contains(targetNode)) {
                throw new IllegalArgumentException("All edges in a component must connect nodes within the component");
            }
        });
    }
}
