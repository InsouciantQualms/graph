/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.ComponentSpace;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.Node;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jgrapht.Graph;

/**
 * Immutable operations for querying components in a JGraphT graph.
 * This class provides query-only operations on components. For mutation operations,
 * see JGraphtMutableComponentOperations.
 *
 * In the new model, components don't contain elements. Instead, elements reference
 * components via their components() method returning Set&lt;Locator&gt;.
 */
public final class JGraphtComponentOperations implements ComponentSpace {

    private final Graph<Node, Edge> graph;
    private final Map<NanoId, List<Component>> componentVersions;

    public JGraphtComponentOperations(final Graph<Node, Edge> graph) {
        this.graph = graph;
        componentVersions = new HashMap<>();
    }

    /**
     * Adds a pre-built component to the component operations.
     * This is used by the builder pattern to populate components.
     */
    void addPrebuiltComponent(final Component component) {
        // Store component version
        componentVersions
                .computeIfAbsent(component.locator().id(), k -> new ArrayList<>())
                .add(component);
    }

    @Override
    public Optional<Component> findActive(final NanoId id) {
        final var versions = componentVersions.get(id);
        if (versions == null) {
            return Optional.empty();
        }

        return versions.stream().filter(c -> c.expired().isEmpty()).max(Comparator.comparingInt(c -> c.locator()
                .version()));
    }

    @Override
    public Optional<Component> findAt(final NanoId id, final Instant timestamp) {
        // Find the component version active at timestamp
        final var versions = componentVersions.get(id);
        if (versions == null) {
            return Optional.empty();
        }

        return versions.stream()
                .filter(c -> !c.created().isAfter(timestamp))
                .filter(c -> c.expired().isEmpty() || c.expired().get().isAfter(timestamp))
                .max(Comparator.comparingInt(c -> c.locator().version()));
    }

    @Override
    public Component find(final Locator locator) {
        final var versions = componentVersions.get(locator.id());
        if (versions == null) {
            throw new IllegalArgumentException("Component not found for locator: " + locator);
        }

        return versions.stream()
                .filter(c -> c.locator().equals(locator))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Component not found for locator: " + locator));
    }

    @Override
    public List<Component> findVersions(final NanoId id) {
        final var versions = componentVersions.get(id);
        return (versions != null) ? new ArrayList<>(versions) : List.of();
    }

    /**
     * Gets all active components.
     * This is used by the builder pattern.
     */
    public List<Component> allActive() {
        return componentVersions.values().stream()
                .flatMap(List::stream)
                .filter(c -> c.expired().isEmpty())
                .toList();
    }

    @Override
    public GraphView asGraph(final Component component) {
        // Create a view containing nodes and edges that reference this component
        return new JGraphtGraphView(graph, nodes(component), edges(component));
    }

    @Override
    public Set<Node> nodes(final Component component) {
        // Find all nodes that reference this component's locator
        return graph.vertexSet().stream()
                .filter(node -> node.components().contains(component.locator()))
                .collect(HashSet::new, Set::add, Set::addAll);
    }

    @Override
    public Set<Edge> edges(final Component component) {
        // Find all edges that reference this component's locator
        return graph.edgeSet().stream()
                .filter(edge -> edge.components().contains(component.locator()))
                .collect(HashSet::new, Set::add, Set::addAll);
    }

    @Override
    public boolean contains(final Component component, final Element element) {
        // Check if the element references this component's locator
        return element.components().contains(component.locator());
    }

    /**
     * Find all elements (nodes and edges) that reference this component.
     */
    public Set<Element> elements(final Component component) {
        return Stream.concat(nodes(component).stream(), edges(component).stream())
                .collect(HashSet::new, Set::add, Set::addAll);
    }
}
