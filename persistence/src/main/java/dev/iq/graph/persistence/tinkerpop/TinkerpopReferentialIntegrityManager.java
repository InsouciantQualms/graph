/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.tinkerpop;

import dev.iq.common.fp.Io;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.common.version.Uid;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.simple.SimpleEdge;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Graph;

/**
 * Manages referential integrity for Tinkerpop graph persistence.
 * Implements the cascading rules for node updates/expiry and component updates.
 */
public final class TinkerpopReferentialIntegrityManager {

    private final Graph graph;
    private final GraphTraversalSource traversal;
    private final TinkerpopNodeRepository nodeRepository;
    private final TinkerpopEdgeRepository edgeRepository;
    private final TinkerpopComponentRepository componentRepository;
    private final Map<Locator, Locator> pendingComponentUpdates = new HashMap<>();

    public TinkerpopReferentialIntegrityManager(
            final Graph graph,
            final TinkerpopNodeRepository nodeRepository,
            final TinkerpopEdgeRepository edgeRepository,
            final TinkerpopComponentRepository componentRepository) {
        this.graph = graph;
        this.traversal = graph.traversal();
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.componentRepository = componentRepository;
    }

    /**
     * Handles referential integrity when a node is expired.
     * All connected edges must also be expired with the same timestamp.
     */
    public void handleNodeExpiry(final Uid nodeId, final Instant expiredAt) {
        Io.withVoid(() -> {
            // Find all active edges connected to this node
            final var connectedEdges = traversal
                    .E()
                    .or(__.has("sourceId", nodeId.code()), __.has("targetId", nodeId.code()))
                    .not(__.has("expired"))
                    .toList();

            // Expire all connected edges with the same timestamp
            connectedEdges.forEach(edge -> edge.property("expired", expiredAt.toString()));
        });
    }

    /**
     * Handles referential integrity when a node is updated.
     * All connected edges must be expired and recreated pointing to the new node version.
     *
     * @param oldNode The node version being updated
     * @param newNode The new node version
     * @param timestamp The timestamp of the update
     * @param alreadyUpdatedEdges Set of edge IDs that have already been updated (to avoid double updates)
     */
    public void handleNodeUpdate(
            final Node oldNode, final Node newNode, final Instant timestamp, final Set<NanoId> alreadyUpdatedEdges) {
        Io.withVoid(() -> {
            // Find all active edges connected to the old node
            final List<Edge> connectedEdges = new ArrayList<>();

            // Find edges where this node is the source
            traversal
                    .E()
                    .has("sourceId", oldNode.locator().id().code())
                    .has("sourceVersionId", oldNode.locator().version())
                    .not(__.has("expired"))
                    .toList()
                    .forEach(e -> {
                        final var edgeId = NanoId.from(e.value("id"));
                        final var edgeVersion = e.<Integer>value("versionId");
                        connectedEdges.add(edgeRepository.find(new Locator(edgeId, edgeVersion)));
                    });

            // Find edges where this node is the target
            traversal
                    .E()
                    .has("targetId", oldNode.locator().id().code())
                    .has("targetVersionId", oldNode.locator().version())
                    .not(__.has("expired"))
                    .toList()
                    .forEach(e -> {
                        final var edgeId = NanoId.from(e.value("id"));
                        final var edgeVersion = e.<Integer>value("versionId");
                        connectedEdges.add(edgeRepository.find(new Locator(edgeId, edgeVersion)));
                    });

            // Process each edge that needs updating
            for (Edge oldEdge : connectedEdges) {
                // Skip if this edge was already updated
                if (alreadyUpdatedEdges.contains(oldEdge.locator().id())) {
                    continue;
                }

                // Mark this edge as updated
                alreadyUpdatedEdges.add((NanoId) oldEdge.locator().id());

                // Expire the current edge
                edgeRepository.expire(oldEdge.locator().id(), timestamp);

                // Determine the new source and target
                final Node newSource =
                        oldEdge.source().locator().equals(oldNode.locator()) ? newNode : oldEdge.source();
                final Node newTarget =
                        oldEdge.target().locator().equals(oldNode.locator()) ? newNode : oldEdge.target();

                // Create new edge with incremented version
                final var newEdgeLocator =
                        new Locator(oldEdge.locator().id(), oldEdge.locator().version() + 1);

                // Apply any pending component updates to the edge components
                final Set<Locator> updatedComponents = applyPendingComponentUpdates(oldEdge.components());

                final var newEdge = new SimpleEdge(
                        newEdgeLocator,
                        oldEdge.type(),
                        newSource,
                        newTarget,
                        oldEdge.data(),
                        updatedComponents,
                        timestamp,
                        java.util.Optional.empty());

                edgeRepository.save(newEdge);
            }
        });
    }

    /**
     * Handles referential integrity when a node is updated.
     * Overloaded version for direct node updates (not from component updates).
     */
    public void handleNodeUpdate(final Node oldNode, final Node newNode, final Instant timestamp) {
        handleNodeUpdate(oldNode, newNode, timestamp, new HashSet<>());
    }

    /**
     * Handles referential integrity when a component is updated.
     * All elements referencing this component must be updated to reference the new version.
     */
    public void handleComponentUpdate(
            final Component oldComponent, final Component newComponent, final Instant timestamp) {
        // Set up pending component updates for edge recreation
        pendingComponentUpdates.put(oldComponent.locator(), newComponent.locator());

        // Track edges that have been updated to avoid double updates
        final Set<NanoId> updatedEdges = new HashSet<>();

        try {
            // FIRST: Update all edges that reference this component directly
            updateEdgesReferencingComponent(oldComponent.locator(), newComponent.locator(), timestamp, updatedEdges);

            // SECOND: Update all nodes that reference this component
            // The node update will only update edges that haven't been updated yet
            updateNodesReferencingComponent(oldComponent.locator(), newComponent.locator(), timestamp, updatedEdges);
        } finally {
            // Clear pending updates
            pendingComponentUpdates.clear();
        }
    }

    private void updateNodesReferencingComponent(
            final Locator oldLocator,
            final Locator newLocator,
            final Instant timestamp,
            final Set<NanoId> alreadyUpdatedEdges) {
        // Nodes no longer have components in the new model
        // This method is now a no-op since only edges can reference components
    }

    private void updateEdgesReferencingComponent(
            final Locator oldLocator,
            final Locator newLocator,
            final Instant timestamp,
            final Set<NanoId> updatedEdges) {
        Io.withVoid(() -> {
            // Find all active edges that contain the old component locator
            final var edgesToUpdate = new ArrayList<Edge>();

            // We need to search through all edges and check their components
            traversal.E().not(__.has("expired")).toList().forEach(edge -> {
                if (edge.property("components").isPresent()) {
                    final var componentsStr = edge.<String>value("components");
                    final var componentRef = oldLocator.id().code() + ":" + oldLocator.version();
                    if (componentsStr.contains(componentRef)) {
                        final var edgeId = NanoId.from(edge.value("id"));
                        final var edgeVersion = edge.<Integer>value("versionId");
                        edgesToUpdate.add(edgeRepository.find(new Locator(edgeId, edgeVersion)));
                    }
                }
            });

            // Update each edge
            for (Edge edge : edgesToUpdate) {
                final NanoId edgeId = (NanoId) edge.locator().id();

                // Mark this edge as updated
                updatedEdges.add(edgeId);

                // Create new components set with updated reference
                final Set<Locator> updatedComponents = new HashSet<>(edge.components());
                updatedComponents.remove(oldLocator);
                updatedComponents.add(newLocator);

                // Expire the old edge version
                edgeRepository.expire(edge.locator().id(), timestamp);

                // Create new edge version with updated components
                final var newEdgeLocator =
                        new Locator(edge.locator().id(), edge.locator().version() + 1);

                final var newEdge = new SimpleEdge(
                        newEdgeLocator,
                        edge.type(),
                        edge.source(),
                        edge.target(),
                        edge.data(),
                        updatedComponents,
                        timestamp,
                        java.util.Optional.empty());

                edgeRepository.save(newEdge);
            }
        });
    }

    /**
     * Applies any pending component updates to a set of component locators.
     */
    private Set<Locator> applyPendingComponentUpdates(final Set<Locator> components) {
        if (pendingComponentUpdates.isEmpty()) {
            return components;
        }

        final Set<Locator> updated = new HashSet<>();
        for (Locator component : components) {
            final Locator newLocator = pendingComponentUpdates.get(component);
            updated.add(newLocator != null ? newLocator : component);
        }
        return updated;
    }
}
