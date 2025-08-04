/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Locator;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Graph;
import dev.iq.graph.model.GraphBuilder;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.simple.SimpleComponent;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jgrapht.graph.DirectedMultigraph;

/**
 * Builder implementation for constructing JGraphT-based graphs.
 * This builder accumulates nodes, edges, and components without any listeners attached,
 * making it ideal for loading from persistence or bulk construction.
 */
public final class JGraphtGraphBuilder implements GraphBuilder {

    private final org.jgrapht.Graph<Node, Edge> jgraphtGraph;
    private final Map<Locator, Node> nodeMap;
    private final Map<Locator, Edge> edgeMap;
    private final Map<Locator, Component> componentMap;
    private final List<Component> components;

    public JGraphtGraphBuilder() {
        this.jgraphtGraph = new DirectedMultigraph<>(null, null, false);
        this.nodeMap = new HashMap<>();
        this.edgeMap = new HashMap<>();
        this.componentMap = new HashMap<>();
        this.components = new ArrayList<>();
    }

    /**
     * Copy constructor for creating a builder from an existing graph.
     * Note: This requires accessing internal implementation details as the immutable
     * operations interfaces don't expose methods to iterate all elements.
     */
    public JGraphtGraphBuilder(final Graph source) {
        this();
        // For now, we don't support copying from an existing graph
        // This would require exposing iteration methods on the immutable interfaces
        // or having a different approach to graph copying
        throw new UnsupportedOperationException("Copying from existing graphs is not yet supported. "
                + "Use GraphBuilder.builder() to create a new graph from scratch.");
    }

    @Override
    public GraphBuilder addNode(final Locator locator, final Type type, final Data data, final Instant created) {
        // Initialize with empty set of components
        final Node node = new SimpleNode(locator, type, data, new HashSet<>(), created, Optional.empty());
        nodeMap.put(locator, node);
        jgraphtGraph.addVertex(node);
        return this;
    }

    @Override
    public GraphBuilder addEdge(
            final Locator locator,
            final Type type,
            final Locator sourceId,
            final Locator targetId,
            final Data data,
            final Instant created) {
        final Node source = nodeMap.get(sourceId);
        if (source == null) {
            throw new IllegalArgumentException("Source node not found: " + sourceId);
        }

        final Node target = nodeMap.get(targetId);
        if (target == null) {
            throw new IllegalArgumentException("Target node not found: " + targetId);
        }

        // Initialize with empty set of components
        final Edge edge =
                new SimpleEdge(locator, type, source, target, data, new HashSet<>(), created, Optional.empty());
        edgeMap.put(locator, edge);
        jgraphtGraph.addEdge(source, target, edge);
        return this;
    }

    @Override
    public GraphBuilder addComponent(
            final Locator locator, final Collection<Locator> elementIds, final Data data, final Instant created) {
        // In the new architecture, we need to:
        // 1. Create the component with just type and data
        // 2. Update the elements to reference this component

        // For now, components have a default Type - this may need to be passed as parameter
        final Type componentType = new SimpleType("component");
        final Component component = new SimpleComponent(locator, componentType, data, created, Optional.empty());
        componentMap.put(locator, component);
        components.add(component);

        // Update elements to reference this component
        for (Locator elementId : elementIds) {
            Node node = nodeMap.get(elementId);
            if (node != null) {
                // Create a new node with updated components set
                Set<Locator> updatedComponents = new HashSet<>(node.components());
                updatedComponents.add(component.locator());
                Node updatedNode = new SimpleNode(
                        node.locator(), node.type(), node.data(), updatedComponents, node.created(), node.expired());
                nodeMap.put(node.locator(), updatedNode);
                jgraphtGraph.removeVertex(node);
                jgraphtGraph.addVertex(updatedNode);
                continue;
            }

            Edge edge = edgeMap.get(elementId);
            if (edge != null) {
                // Create a new edge with updated components set
                Set<Locator> updatedComponents = new HashSet<>(edge.components());
                updatedComponents.add(component.locator());
                Edge updatedEdge = new SimpleEdge(
                        edge.locator(),
                        edge.type(),
                        edge.source(),
                        edge.target(),
                        edge.data(),
                        updatedComponents,
                        edge.created(),
                        edge.expired());
                edgeMap.put(edge.locator(), updatedEdge);
                jgraphtGraph.removeEdge(edge);
                jgraphtGraph.addEdge(edge.source(), edge.target(), updatedEdge);
                continue;
            }

            throw new IllegalArgumentException("Element not found: " + elementId);
        }

        return this;
    }

    @Override
    public GraphBuilder validate() {
        // Basic validation - can be enhanced as needed
        if (jgraphtGraph.vertexSet().isEmpty()) {
            throw new IllegalStateException("Graph must contain at least one node");
        }

        // Validate all edges have valid endpoints (already validated during construction)
        // Validate components reference valid elements (already validated during construction)

        return this;
    }

    @Override
    public Graph build() {
        // Create a new JGraphtGraphOperations without a listener
        // The caller can add a listener after construction if needed
        return new JGraphtGraphOperations(jgraphtGraph, components);
    }
}
