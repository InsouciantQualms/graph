/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Locator;
import dev.iq.common.version.Uid;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.operations.ComponentOperations;
import dev.iq.graph.model.operations.ComponentStrategy;
import dev.iq.graph.model.simple.SimpleComponent;
import dev.iq.graph.model.simple.SimpleEdge;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.AsSubgraph;

/**
 * JGraphT implementation of mutable component operations.
 * Used during graph construction and bulk operations.
 *
 * In the new architecture, components are pure metadata objects (Type + Data).
 * Elements reference components via Set&lt;Locator&gt;.
 * When a component is updated, all elements referencing it must be updated.
 */
public final class JGraphtComponentOperations implements ComponentOperations {

    private final Graph<Node, Edge> graph;
    private final ComponentStrategy componentStrategy;
    private final JGraphtNodeOperations nodeOperations;
    private final JGraphtEdgeOperations edgeOperations;

    public JGraphtComponentOperations(
            final Graph<Node, Edge> graph,
            final JGraphtNodeOperations nodeOperations,
            final JGraphtEdgeOperations edgeOperations) {
        this(graph, nodeOperations, edgeOperations, new SeparateComponentStrategy(graph));
    }

    public JGraphtComponentOperations(
            final Graph<Node, Edge> graph,
            final JGraphtNodeOperations nodeOperations,
            final JGraphtEdgeOperations edgeOperations,
            final ComponentStrategy componentStrategy) {
        this.graph = graph;
        this.componentStrategy = componentStrategy;
        this.nodeOperations = nodeOperations;
        this.edgeOperations = edgeOperations;
    }

    @Override
    public Component add(final Type type, final Data data, final Instant timestamp) {
        final var locator = Locator.generate();
        final var component = new SimpleComponent(locator, type, data, timestamp, Optional.empty());

        // Store component version
        componentStrategy.store(component);

        return component;
    }

    @Override
    public Component update(final Uid id, final Type type, final Data data, final Instant timestamp) {
        JGraphtHelper.require(componentStrategy.findActive(id), id, "Component");
        return performUpdate(id, type, data, timestamp);
    }

    private Component performUpdate(final Uid id, final Type type, final Data data, final Instant timestamp) {
        // First expire the existing component
        final var expired = expire(id, timestamp);

        // Create new version with incremented locator
        final var incremented = expired.locator().next();
        final var newComponent = new SimpleComponent(incremented, type, data, timestamp, Optional.empty());

        // Store new version
        componentStrategy.store(newComponent);

        // Update all elements that reference this component
        // We need to be careful about the order of operations to avoid duplicate edge updates
        updateElementReferences(expired.locator(), newComponent.locator(), timestamp);

        return newComponent;
    }

    @Override
    public Component expire(final Uid id, final Instant timestamp) {
        final var component = JGraphtHelper.require(componentStrategy.findActive(id), id, "Component");

        // Create expired version
        final var expiredComponent = new SimpleComponent(
                component.locator(), component.type(), component.data(), component.created(), Optional.of(timestamp));

        // Update stored version
        componentStrategy.store(expiredComponent);

        return expiredComponent;
    }

    /**
     * Updates all elements that reference the old component locator to reference the new one.
     * This maintains referential integrity when components are updated.
     */
    private void updateElementReferences(final Locator oldLocator, final Locator newLocator, final Instant timestamp) {
        // Since nodes don't have components, we only need to update edges
        final Set<Edge> edgesToUpdate = componentStrategy.findEdgesReferencingComponent(oldLocator);

        for (final Edge edge : edgesToUpdate) {
            // Create new components set with updated reference
            final Set<Locator> updatedComponents = new HashSet<>(edge.components());
            updatedComponents.remove(oldLocator);
            updatedComponents.add(newLocator);

            // Update the edge with new component references
            // This requires recreating the edge with the new component set
            graph.removeEdge(edge);
            final Edge newEdge = new SimpleEdge(
                    edge.locator().next(),
                    edge.type(),
                    edge.source(),
                    edge.target(),
                    edge.data(),
                    updatedComponents,
                    timestamp,
                    Optional.empty());
            graph.addEdge(edge.source(), edge.target(), newEdge);
        }
    }

    /**
     * Adds a pre-built component to the component operations.
     * This is used by the builder pattern to populate components.
     */
    public void addPrebuiltComponent(final Component component) {
        // Store component version
        componentStrategy.store(component);
    }

    /**
     * Gets all active components.
     * This is used by the builder pattern.
     */
    public List<Component> allActive() {
        return componentStrategy.allActive();
    }

    @Override
    public Optional<Component> findActive(final Uid id) {
        return componentStrategy.findActive(id);
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
