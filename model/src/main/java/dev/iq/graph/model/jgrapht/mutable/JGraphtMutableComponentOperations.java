/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht.mutable;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.jgrapht.JGraphtOperationsHelper;
import dev.iq.graph.model.operations.ComponentOperations;
import dev.iq.graph.model.simple.SimpleComponent;
import java.time.Instant;
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
 * JGraphT implementation of mutable component operations.
 * Used during graph construction and bulk operations.
 *
 * In the new architecture, components are pure metadata objects (Type + Data).
 * Elements reference components via Set&lt;Locator&gt;.
 * When a component is updated, all elements referencing it must be updated.
 */
public final class JGraphtMutableComponentOperations implements ComponentOperations {

    private final Graph<Node, Edge> graph;
    private final Map<NanoId, List<Component>> componentVersions;
    private final JGraphtMutableNodeOperations nodeOperations;
    private final JGraphtMutableEdgeOperations edgeOperations;

    public JGraphtMutableComponentOperations(
            final Graph<Node, Edge> graph,
            final JGraphtMutableNodeOperations nodeOperations,
            final JGraphtMutableEdgeOperations edgeOperations) {
        this.graph = graph;
        componentVersions = new HashMap<>();
        this.nodeOperations = nodeOperations;
        this.edgeOperations = edgeOperations;
    }

    @Override
    public Component add(final Type type, final Data data, final Instant timestamp) {
        final var locator = Locator.generate();
        final var component = new SimpleComponent(locator, type, data, timestamp, Optional.empty());

        // Store component version
        componentVersions.computeIfAbsent(locator.id(), k -> new ArrayList<>()).add(component);

        return component;
    }

    @Override
    public Component update(final NanoId id, final Data data, final Instant timestamp) {
        final var existingComponent = JGraphtOperationsHelper.validateForExpiry(findActive(id), id, "Component");
        return performUpdate(id, existingComponent.type(), data, timestamp);
    }

    @Override
    public Component update(final NanoId id, final Type type, final Data data, final Instant timestamp) {
        JGraphtOperationsHelper.validateForExpiry(findActive(id), id, "Component");
        return performUpdate(id, type, data, timestamp);
    }

    private Component performUpdate(final NanoId id, final Type type, final Data data, final Instant timestamp) {
        // First expire the existing component
        final var expired = expire(id, timestamp);

        // Create new version with incremented locator
        final var incremented = expired.locator().increment();
        final var newComponent = new SimpleComponent(incremented, type, data, timestamp, Optional.empty());

        // Store new version
        componentVersions.get(id).add(newComponent);

        // Update all elements that reference this component
        // We need to be careful about the order of operations to avoid duplicate edge updates
        updateElementReferences(expired.locator(), newComponent.locator(), timestamp);

        return newComponent;
    }

    @Override
    public Component expire(final NanoId id, final Instant timestamp) {
        final var component = JGraphtOperationsHelper.validateForExpiry(findActive(id), id, "Component");

        // Create expired version
        final var expiredComponent = new SimpleComponent(
                component.locator(), component.type(), component.data(), component.created(), Optional.of(timestamp));

        // Update stored version
        final var versions = componentVersions.get(id);
        if (versions != null) {
            // Replace the active version with expired version
            versions.removeIf(c -> c.locator().equals(component.locator()));
            versions.add(expiredComponent);
        }

        return expiredComponent;
    }

    /**
     * Updates all elements that reference the old component locator to reference the new one.
     * This maintains referential integrity when components are updated.
     */
    private void updateElementReferences(final Locator oldLocator, final Locator newLocator, final Instant timestamp) {
        // Set up the component update mapping for edge recreation
        final Map<Locator, Locator> componentUpdateMap = new HashMap<>();
        componentUpdateMap.put(oldLocator, newLocator);
        nodeOperations.setPendingComponentUpdates(componentUpdateMap);

        try {
            // Collect nodes that need updating and track edges that will be updated
            final Set<Node> nodesToUpdate = collectNodesToUpdate(oldLocator);
            final Set<NanoId> edgesUpdatedByNodeUpdate = collectEdgesUpdatedByNodeUpdate(nodesToUpdate);

            // Update nodes - their edges will be recreated with updated component references
            updateNodes(nodesToUpdate, oldLocator, newLocator, timestamp);

            // Update any edges that weren't updated as part of node updates
            updateRemainingEdges(oldLocator, newLocator, edgesUpdatedByNodeUpdate, timestamp);
        } finally {
            // Clear the pending updates
            nodeOperations.clearPendingComponentUpdates();
        }
    }

    /**
     * Collects all nodes that need to be updated because they reference the old component.
     */
    private Set<Node> collectNodesToUpdate(final Locator oldLocator) {
        final Set<Node> nodesToUpdate = new HashSet<>();
        for (final Node node : graph.vertexSet()) {
            if (node.expired().isEmpty() && node.components().contains(oldLocator)) {
                nodesToUpdate.add(node);
            }
        }
        return nodesToUpdate;
    }

    /**
     * Collects IDs of edges that will be updated as part of node updates.
     */
    private Set<NanoId> collectEdgesUpdatedByNodeUpdate(final Set<Node> nodesToUpdate) {
        final Set<NanoId> edgesUpdatedByNodeUpdate = new HashSet<>();
        for (final Node node : nodesToUpdate) {
            // When a node is updated, all its connected edges are recreated
            graph.incomingEdgesOf(node).stream()
                    .filter(e -> e.expired().isEmpty())
                    .forEach(e -> edgesUpdatedByNodeUpdate.add(e.locator().id()));
            graph.outgoingEdgesOf(node).stream()
                    .filter(e -> e.expired().isEmpty())
                    .forEach(e -> edgesUpdatedByNodeUpdate.add(e.locator().id()));
        }
        return edgesUpdatedByNodeUpdate;
    }

    /**
     * Updates nodes with new component references.
     */
    private void updateNodes(
            final Set<Node> nodesToUpdate,
            final Locator oldLocator,
            final Locator newLocator,
            final Instant timestamp) {
        for (final Node node : nodesToUpdate) {
            // Create new components set with updated reference
            final Set<Locator> updatedComponents = new HashSet<>(node.components());
            updatedComponents.remove(oldLocator);
            updatedComponents.add(newLocator);

            // Update the node with new component references
            nodeOperations.updateComponents(node.locator().id(), updatedComponents, timestamp);
        }
    }

    /**
     * Updates edges that weren't updated as part of node updates.
     */
    private void updateRemainingEdges(
            final Locator oldLocator,
            final Locator newLocator,
            final Set<NanoId> edgesUpdatedByNodeUpdate,
            final Instant timestamp) {
        final Set<Edge> edgesToUpdate = new HashSet<>();
        for (final Edge edge : graph.edgeSet()) {
            if (edge.expired().isEmpty()
                    && edge.components().contains(oldLocator)
                    && !edgesUpdatedByNodeUpdate.contains(edge.locator().id())) {
                edgesToUpdate.add(edge);
            }
        }

        for (final Edge edge : edgesToUpdate) {
            // Create new components set with updated reference
            final Set<Locator> updatedComponents = new HashSet<>(edge.components());
            updatedComponents.remove(oldLocator);
            updatedComponents.add(newLocator);

            // Update the edge with new component references
            edgeOperations.updateComponents(edge.locator().id(), updatedComponents, timestamp);
        }
    }

    /**
     * Adds a pre-built component to the component operations.
     * This is used by the builder pattern to populate components.
     */
    public void addPrebuiltComponent(final Component component) {
        // Store component version
        componentVersions
                .computeIfAbsent(component.locator().id(), k -> new ArrayList<>())
                .add(component);
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

    private Optional<Component> findActive(final NanoId id) {
        final var versions = componentVersions.get(id);
        if (versions == null) {
            return Optional.empty();
        }

        return versions.stream().filter(c -> c.expired().isEmpty()).max(Comparator.comparingInt(c -> c.locator()
                .version()));
    }
}
