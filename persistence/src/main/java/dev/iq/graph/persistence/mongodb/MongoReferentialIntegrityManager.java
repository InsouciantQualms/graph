/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.not;
import static com.mongodb.client.model.Filters.or;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;

/**
 * Manages referential integrity for MongoDB graph persistence.
 * Implements the cascading rules for node updates/expiry and component updates.
 */
public final class MongoReferentialIntegrityManager {

    private final MongoDatabase database;
    private final MongoNodeRepository nodeRepository;
    private final MongoEdgeRepository edgeRepository;
    private final MongoComponentRepository componentRepository;
    private final ClientSession clientSession;
    private final Map<Locator, Locator> pendingComponentUpdates = new HashMap<>();

    public MongoReferentialIntegrityManager(
            final MongoDatabase database,
            final MongoNodeRepository nodeRepository,
            final MongoEdgeRepository edgeRepository,
            final MongoComponentRepository componentRepository,
            final ClientSession clientSession) {
        this.database = database;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.componentRepository = componentRepository;
        this.clientSession = clientSession;
    }

    /**
     * Handles referential integrity when a node is expired.
     * All connected edges must also be expired with the same timestamp.
     */
    public void handleNodeExpiry(final NanoId nodeId, final Instant expiredAt) {
        final MongoCollection<Document> edgeCollection = database.getCollection("edges");

        // Find all active edges connected to this node
        final var connectedEdgesFilter =
                and(or(eq("sourceId", nodeId.id()), eq("targetId", nodeId.id())), not(exists("expired")));

        // Expire all connected edges with the same timestamp
        if (clientSession != null) {
            edgeCollection.updateMany(
                    clientSession,
                    connectedEdgesFilter,
                    new Document("$set", new Document("expired", formatTimestamp(expiredAt))));
        } else {
            edgeCollection.updateMany(
                    connectedEdgesFilter, new Document("$set", new Document("expired", formatTimestamp(expiredAt))));
        }
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
        final MongoCollection<Document> edgeCollection = database.getCollection("edges");

        // Find all active edges connected to either the old or new node
        final List<Edge> connectedEdges = new ArrayList<>();

        // Find edges where this node is the source
        final var sourceEdges =
                edgeCollection.find(and(eq("sourceId", oldNode.locator().id().id()), not(exists("expired"))));

        for (Document doc : sourceEdges) {
            final NanoId edgeId = new NanoId(doc.getString("id"));
            // Skip if this edge was already updated
            if (alreadyUpdatedEdges.contains(edgeId)) {
                continue;
            }

            final Edge edge = edgeRepository.find(extractEdgeLocator(doc));
            // Only process if this edge needs updating (connected to old node version)
            if (edge.source().locator().version() == oldNode.locator().version()) {
                connectedEdges.add(edge);
            }
        }

        // Find edges where this node is the target
        final var targetEdges =
                edgeCollection.find(and(eq("targetId", oldNode.locator().id().id()), not(exists("expired"))));

        for (Document doc : targetEdges) {
            final NanoId edgeId = new NanoId(doc.getString("id"));
            // Skip if this edge was already updated
            if (alreadyUpdatedEdges.contains(edgeId)) {
                continue;
            }

            final Edge edge = edgeRepository.find(extractEdgeLocator(doc));
            // Only process if this edge needs updating (connected to old node version)
            if (edge.target().locator().version() == oldNode.locator().version()) {
                connectedEdges.add(edge);
            }
        }

        // Process each edge that needs updating
        for (Edge edge : connectedEdges) {
            // Mark this edge as updated
            alreadyUpdatedEdges.add(edge.locator().id());

            // Expire the current edge
            edgeRepository.expire(edge.locator().id(), timestamp);

            // Determine the new source and target
            final Node newSource =
                    edge.source().locator().id().equals(oldNode.locator().id()) ? newNode : edge.source();
            final Node newTarget =
                    edge.target().locator().id().equals(oldNode.locator().id()) ? newNode : edge.target();

            // Create new edge with incremented version
            final var newEdgeLocator =
                    new Locator(edge.locator().id(), edge.locator().version() + 1);

            // Apply any pending component updates to the edge components
            final Set<Locator> updatedComponents = applyPendingComponentUpdates(edge.components());

            final var newEdge = new SimpleEdge(
                    newEdgeLocator,
                    edge.type(),
                    newSource,
                    newTarget,
                    edge.data(),
                    updatedComponents,
                    timestamp,
                    java.util.Optional.empty());

            edgeRepository.save(newEdge);
        }
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
        final MongoCollection<Document> nodeCollection = database.getCollection("nodes");

        // Find all active nodes that contain the old component locator
        final var filter = and(
                not(exists("expired")),
                eq(
                        "components",
                        new Document().append("id", oldLocator.id().id()).append("versionId", oldLocator.version())));

        final var nodes = nodeCollection.find(filter);
        for (Document doc : nodes) {
            final Node node = nodeRepository.find(extractNodeLocator(doc));

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
                    newNodeLocator, node.type(), node.data(), updatedComponents, timestamp, java.util.Optional.empty());

            nodeRepository.save(newNode);

            // Handle cascading edge updates for this node update, passing the set of already updated edges
            handleNodeUpdate(node, newNode, timestamp, alreadyUpdatedEdges);
        }
    }

    private void updateEdgesReferencingComponent(
            final Locator oldLocator,
            final Locator newLocator,
            final Instant timestamp,
            final Set<NanoId> updatedEdges) {
        final MongoCollection<Document> edgeCollection = database.getCollection("edges");

        // Find all active edges that contain the old component locator
        final var filter = and(
                not(exists("expired")),
                eq(
                        "components",
                        new Document().append("id", oldLocator.id().id()).append("versionId", oldLocator.version())));

        final var edges = edgeCollection.find(filter);
        for (Document doc : edges) {
            final NanoId edgeId = new NanoId(doc.getString("id"));
            final Edge edge = edgeRepository.find(extractEdgeLocator(doc));

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
    }

    private Locator extractNodeLocator(final Document document) {
        final var id = new NanoId(document.getString("id"));
        final var versionId = document.getInteger("versionId");
        return new Locator(id, versionId);
    }

    private Locator extractEdgeLocator(final Document document) {
        final var id = new NanoId(document.getString("id"));
        final var versionId = document.getInteger("versionId");
        return new Locator(id, versionId);
    }

    private String formatTimestamp(final Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS).toString();
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
