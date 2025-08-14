/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Uid;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.ComponentSpace;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.View;
import dev.iq.graph.model.operations.ComponentOperations;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;

/**
 * JGraphT implementation of ComponentSpace.
 * Represents a subgraph view for a single component.
 */
public final class JGraphtComponentSpace implements ComponentSpace {

    private final Component component;
    private final Graph<Node, Edge> fullGraph;
    private final ComponentOperations componentOperations;

    public JGraphtComponentSpace(
            final Component component,
            final Graph<Node, Edge> fullGraph,
            final ComponentOperations componentOperations) {
        this.component = component;
        this.fullGraph = fullGraph;
        this.componentOperations = componentOperations;
    }

    /**
     * Get all edges that were part of this component as of a specific instant.
     * This method is not part of the ComponentSpace interface but provides
     * the ability to recreate component state at a point in time.
     */
    public Set<Edge> componentEdgesAsOf(final Instant timestamp) {
        // Find all edges that:
        // 1. Reference this component
        // 2. Were created before or at the timestamp
        // 3. Were not expired before the timestamp
        return fullGraph.edgeSet().stream()
                .filter(edge -> edge.components().contains(component.locator()))
                .filter(edge -> !edge.created().isAfter(timestamp))
                .filter(edge -> edge.expired().isEmpty() || edge.expired().get().isAfter(timestamp))
                .collect(Collectors.toSet());
    }

    /**
     * Get all nodes within this component as of a specific instant (derived from edges).
     * This method is not part of the ComponentSpace interface but provides
     * the ability to recreate component state at a point in time.
     */
    public Set<Node> componentNodesAsOf(final Instant timestamp) {
        final Set<Node> nodes = new HashSet<>();
        for (final Edge edge : componentEdgesAsOf(timestamp)) {
            // Add nodes that existed at the timestamp
            if (!edge.source().created().isAfter(timestamp)
                    && (edge.source().expired().isEmpty()
                            || edge.source().expired().get().isAfter(timestamp))) {
                nodes.add(edge.source());
            }
            if (!edge.target().created().isAfter(timestamp)
                    && (edge.target().expired().isEmpty()
                            || edge.target().expired().get().isAfter(timestamp))) {
                nodes.add(edge.target());
            }
        }
        return nodes;
    }

    @Override
    public View view() {
        // Create a subgraph view containing only the component's elements
        final Set<Edge> edges = componentEdges();
        final Set<Node> nodes = componentNodes();
        final Graph<Node, Edge> subgraph = new AsSubgraph<>(fullGraph, nodes, edges);
        return new JGraphtGraphView(subgraph);
    }

    @Override
    public Component component() {
        return component;
    }

    @Override
    public Set<Edge> componentEdges() {
        // Find all edges that reference this component
        return fullGraph.edgeSet().stream()
                .filter(edge -> edge.expired().isEmpty())
                .filter(edge -> edge.components().contains(component.locator()))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Node> componentNodes() {
        // Derive nodes from the component's edges
        final Set<Node> nodes = new HashSet<>();
        for (final Edge edge : componentEdges()) {
            nodes.add(edge.source());
            nodes.add(edge.target());
        }
        return nodes;
    }

    @Override
    public Set<Component> componentsForElement(final Uid id) {
        final Set<Component> components = new HashSet<>();

        // Find the element by ID
        final var nodeOpt = fullGraph.vertexSet().stream()
                .filter(n -> n.locator().id().equals(id))
                .findFirst();

        if (nodeOpt.isPresent()) {
            // For nodes, check all connected edges
            final Node node = nodeOpt.get();
            fullGraph.edgesOf(node).stream()
                    .filter(e -> e.expired().isEmpty())
                    .flatMap(e -> e.components().stream())
                    .forEach(locator ->
                            componentOperations.findActive(locator.id()).ifPresent(components::add));
        } else {
            // Check if it's an edge
            fullGraph.edgeSet().stream()
                    .filter(e -> e.locator().id().equals(id))
                    .filter(e -> e.expired().isEmpty())
                    .flatMap(e -> e.components().stream())
                    .forEach(locator ->
                            componentOperations.findActive(locator.id()).ifPresent(components::add));
        }

        return components;
    }
}
