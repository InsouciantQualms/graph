/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Locator;
import dev.iq.common.version.SimpleVersionedFinder;
import dev.iq.common.version.Uid;
import dev.iq.common.version.VersionedFinder;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.ComponentSpace;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.GraphSpace;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.View;
import dev.iq.graph.model.operations.ComponentOperations;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.ListenableGraph;
import org.jgrapht.event.GraphListener;
import org.jgrapht.graph.DefaultListenableGraph;
import org.jgrapht.graph.DirectedMultigraph;

/**
 * JGraphT implementation of GraphSpace interface.
 * Serves as a container for all graph elements and operations.
 */
public final class JGraphtGraphSpace implements GraphSpace {

    /** JGraphT delegated in memory graph. */
    private final Graph<Node, Edge> graph;

    /** Component operations. */
    private final SimpleVersionedFinder<Component> components;

    /** Component operations for managing components. */
    private final ComponentOperations componentOperations;

    /** Creates a new graph with a listener attached. */
    public JGraphtGraphSpace(final GraphListener<Node, Edge> listener) {
        this(new DirectedMultigraph<>(null, null, true), listener); // Allow self-loops
    }

    /**
     * Creates a graph space with a pre-populated graph and listener.
     *
     * @param graph the pre-populated graph to use
     * @param listener the graph listener to attach
     */
    public JGraphtGraphSpace(final Graph<Node, Edge> graph, final GraphListener<Node, Edge> listener) {
        // Wrap the graph with a listener if not already listenable
        if (graph instanceof ListenableGraph) {
            this.graph = graph;
            ((ListenableGraph<Node, Edge>) graph).addGraphListener(listener);
        } else {
            final ListenableGraph<Node, Edge> wrapper = new DefaultListenableGraph<>(graph);
            wrapper.addGraphListener(listener);
            this.graph = wrapper;
        }

        components = new SimpleVersionedFinder<>();

        // Initialize component operations with node and edge operations
        final var edgeOperations = new JGraphtEdgeOperations(this.graph);
        final var nodeOperations = new JGraphtNodeOperations(this.graph, edgeOperations);
        componentOperations = new JGraphtComponentOperations(this.graph, nodeOperations, edgeOperations);
    }

    /** {@inheritDoc} */
    @Override
    public VersionedFinder<Component> components() {

        return components;
    }

    /** {@inheritDoc} */
    @Override
    public View view() {

        return new JGraphtGraphView(graph);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ComponentSpace> componentSpace(final Uid componentId) {
        return componentOperations
                .findActive(componentId)
                .map(component -> new JGraphtComponentSpace(component, graph, componentOperations));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ComponentSpace> componentSpace(final Locator componentLocator) {
        return componentOperations
                .findActive(componentLocator.id())
                .filter(c -> c.locator().equals(componentLocator))
                .map(component -> new JGraphtComponentSpace(component, graph, componentOperations));
    }

    /** {@inheritDoc} */
    @Override
    public Set<ComponentSpace> componentSpacesContaining(final Element element) {
        final Set<ComponentSpace> spaces = new HashSet<>();

        // Find all edges that might reference components
        final Set<Locator> referencedComponents = new HashSet<>();
        if (element instanceof Edge edge) {
            referencedComponents.addAll(edge.components());
        }

        // Also check edges connected to this element if it's a node
        if (element instanceof Node node) {
            graph.edgesOf(node).stream()
                    .filter(e -> e.expired().isEmpty())
                    .flatMap(e -> e.components().stream())
                    .forEach(referencedComponents::add);
        }

        // Create ComponentSpace for each referenced component
        for (final Locator locator : referencedComponents) {
            componentOperations
                    .findActive(locator.id())
                    .filter(c -> c.locator().equals(locator))
                    .map(component -> new JGraphtComponentSpace(component, graph, componentOperations))
                    .ifPresent(spaces::add);
        }

        return spaces;
    }

    /** {@inheritDoc} */
    @Override
    public ComponentSpace createComponent(
            final Set<Edge> edges, final Type type, final Data data, final Instant timestamp) {
        // Validate the edges form a valid component
        JGraphtComponentOperations.validateComponentElements(new HashSet<>(edges), graph);

        // Create the component
        final Component component = componentOperations.add(type, data, timestamp);

        // Update edges to reference the new component
        for (final Edge edge : edges) {
            final Set<Locator> updatedComponents = new HashSet<>(edge.components());
            updatedComponents.add(component.locator());
            // This would require EdgeOperations to have updateComponents method
            // For now, we'll assume edges are created with component references
        }

        return new JGraphtComponentSpace(component, graph, componentOperations);
    }
}
