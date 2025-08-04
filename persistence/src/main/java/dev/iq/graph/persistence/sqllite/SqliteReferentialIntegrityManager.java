/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import dev.iq.common.fp.Io;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages referential integrity for SQLite graph persistence.
 * Implements the cascading rules for node updates/expiry and component updates.
 */
public final class SqliteReferentialIntegrityManager {

    private final SqliteHandleProvider session;
    private final SqliteNodeRepository nodeRepository;
    private final SqliteEdgeRepository edgeRepository;
    private final SqliteComponentRepository componentRepository;
    private final Map<Locator, Locator> pendingComponentUpdates = new HashMap<>();

    public SqliteReferentialIntegrityManager(
            final SqliteHandleProvider session,
            final SqliteNodeRepository nodeRepository,
            final SqliteEdgeRepository edgeRepository,
            final SqliteComponentRepository componentRepository) {
        this.session = session;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.componentRepository = componentRepository;
    }

    /**
     * Handles referential integrity when a node is expired.
     * All connected edges must also be expired with the same timestamp.
     */
    public void handleNodeExpiry(final NanoId nodeId, final Instant expiredAt) {
        Io.withVoid(() -> {
            final var handle = session.handle();

            // Find all active edges connected to this node
            final var sql =
                    """
                    UPDATE edge
                    SET expired = :expired
                    WHERE expired IS NULL
                      AND (
                        (source_id = :node_id) OR
                        (target_id = :node_id)
                      )
                    """;

            handle.createUpdate(sql)
                    .bind("expired", expiredAt.toString())
                    .bind("node_id", nodeId.id())
                    .execute();
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
            final var handle = session.handle();

            // Find all active edges connected to the old node
            final List<Edge> connectedEdges = new ArrayList<>();

            // Find edges where this node is the source
            final var sourceEdgesSql =
                    """
                    SELECT DISTINCT e.id, e.version_id, e.type, e.source_id, e.source_version_id,
                                    e.target_id, e.target_version_id, e.created, e.expired
                    FROM edge e
                    WHERE e.source_id = :node_id
                      AND e.source_version_id = :node_version
                      AND e.expired IS NULL
                    """;

            handle.createQuery(sourceEdgesSql)
                    .bind("node_id", oldNode.locator().id().id())
                    .bind("node_version", oldNode.locator().version())
                    .map((rs, ctx) -> {
                        final var edgeId = new NanoId(rs.getString("id"));
                        final var edgeVersion = rs.getInt("version_id");
                        return new Locator(edgeId, edgeVersion);
                    })
                    .list()
                    .forEach(locator -> connectedEdges.add(edgeRepository.find(locator)));

            // Find edges where this node is the target
            final var targetEdgesSql =
                    """
                    SELECT DISTINCT e.id, e.version_id, e.type, e.source_id, e.source_version_id,
                                    e.target_id, e.target_version_id, e.created, e.expired
                    FROM edge e
                    WHERE e.target_id = :node_id
                      AND e.target_version_id = :node_version
                      AND e.expired IS NULL
                    """;

            handle.createQuery(targetEdgesSql)
                    .bind("node_id", oldNode.locator().id().id())
                    .bind("node_version", oldNode.locator().version())
                    .map((rs, ctx) -> {
                        final var edgeId = new NanoId(rs.getString("id"));
                        final var edgeVersion = rs.getInt("version_id");
                        return new Locator(edgeId, edgeVersion);
                    })
                    .list()
                    .forEach(locator -> connectedEdges.add(edgeRepository.find(locator)));

            // Process each edge that needs updating
            for (Edge oldEdge : connectedEdges) {
                // Skip if this edge was already updated
                if (alreadyUpdatedEdges.contains(oldEdge.locator().id())) {
                    continue;
                }

                // Mark this edge as updated
                alreadyUpdatedEdges.add(oldEdge.locator().id());

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
        Io.withVoid(() -> {
            final var handle = session.handle();

            // Find all active nodes that contain the old component locator
            final var sql =
                    """
                    SELECT DISTINCT n.id, n.version_id
                    FROM node n
                    JOIN node_components nc ON n.id = nc.node_id AND n.version_id = nc.node_version
                    WHERE n.expired IS NULL
                      AND nc.component_id = :component_id
                      AND nc.component_version = :component_version
                    """;

            final var nodesToUpdate = handle.createQuery(sql)
                    .bind("component_id", oldLocator.id().id())
                    .bind("component_version", oldLocator.version())
                    .map((rs, ctx) -> {
                        final var nodeId = new NanoId(rs.getString("id"));
                        final var nodeVersion = rs.getInt("version_id");
                        return new Locator(nodeId, nodeVersion);
                    })
                    .list();

            for (Locator nodeLocator : nodesToUpdate) {
                final Node node = nodeRepository.find(nodeLocator);

                // Create new components set with updated reference
                final Set<Locator> updatedComponents = new HashSet<>(node.components());
                updatedComponents.remove(oldLocator);
                updatedComponents.add(newLocator);

                // Expire the old node version
                nodeRepository.expire(node.locator().id(), timestamp);

                // Create new node version with updated components
                final var newNodeLocator =
                        new Locator(node.locator().id(), node.locator().version() + 1);

                final var newNode = new SimpleNode(
                        newNodeLocator,
                        node.type(),
                        node.data(),
                        updatedComponents,
                        timestamp,
                        java.util.Optional.empty());

                nodeRepository.save(newNode);

                // Handle cascading edge updates for this node update, passing the set of already updated edges
                handleNodeUpdate(node, newNode, timestamp, alreadyUpdatedEdges);
            }
        });
    }

    private void updateEdgesReferencingComponent(
            final Locator oldLocator,
            final Locator newLocator,
            final Instant timestamp,
            final Set<NanoId> updatedEdges) {
        Io.withVoid(() -> {
            final var handle = session.handle();

            // Find all active edges that contain the old component locator
            final var sql =
                    """
                    SELECT DISTINCT e.id, e.version_id
                    FROM edge e
                    JOIN edge_components ec ON e.id = ec.edge_id AND e.version_id = ec.edge_version
                    WHERE e.expired IS NULL
                      AND ec.component_id = :component_id
                      AND ec.component_version = :component_version
                    """;

            final var edgesToUpdate = handle.createQuery(sql)
                    .bind("component_id", oldLocator.id().id())
                    .bind("component_version", oldLocator.version())
                    .map((rs, ctx) -> {
                        final var edgeId = new NanoId(rs.getString("id"));
                        final var edgeVersion = rs.getInt("version_id");
                        return new Locator(edgeId, edgeVersion);
                    })
                    .list();

            for (Locator edgeLocator : edgesToUpdate) {
                final Edge edge = edgeRepository.find(edgeLocator);
                final NanoId edgeId = edge.locator().id();

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
