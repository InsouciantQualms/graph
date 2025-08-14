/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Locator;
import dev.iq.common.version.Uid;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.operations.ComponentStrategy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jgrapht.Graph;

/**
 * Component storage strategy that maintains components separately from the graph.
 * This is the traditional approach suitable for non-graph databases.
 */
public final class SeparateComponentStrategy implements ComponentStrategy {

    private final Map<Uid, List<Component>> componentVersions;
    private final Graph<Node, Edge> graph;

    public SeparateComponentStrategy(final Graph<Node, Edge> graph) {
        this.componentVersions = new HashMap<>();
        this.graph = graph;
    }

    @Override
    public void store(final Component component) {
        final var versions =
                componentVersions.computeIfAbsent(component.locator().id(), k -> new ArrayList<>());

        // Replace existing version if it exists (for expiry updates)
        versions.removeIf(c -> c.locator().equals(component.locator()));
        versions.add(component);
    }

    @Override
    public Optional<Component> findActive(final Uid id) {
        final var versions = componentVersions.get(id);
        if (versions == null) {
            return Optional.empty();
        }

        return versions.stream().filter(c -> c.expired().isEmpty()).max(Comparator.comparingInt(c -> c.locator()
                .version()));
    }

    @Override
    public List<Component> findVersions(final Uid id) {
        final var versions = componentVersions.get(id);
        return versions != null ? new ArrayList<>(versions) : List.of();
    }

    @Override
    public List<Component> allActive() {
        return componentVersions.values().stream()
                .flatMap(List::stream)
                .filter(c -> c.expired().isEmpty())
                .toList();
    }

    @Override
    public void updateReferences(final Locator oldLocator, final Locator newLocator) {
        // In separate storage, references are maintained in the graph edges
        // This method is called by the operations layer to coordinate updates
    }

    @Override
    public Set<Edge> findEdgesReferencingComponent(final Locator componentLocator) {
        final Set<Edge> referencingEdges = new HashSet<>();
        for (final Edge edge : graph.edgeSet()) {
            if (edge.expired().isEmpty() && edge.components().contains(componentLocator)) {
                referencingEdges.add(edge);
            }
        }
        return referencingEdges;
    }

    @Override
    public void clear() {
        componentVersions.clear();
    }
}
