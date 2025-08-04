/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht.mutable;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.common.version.Versions;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.jgrapht.JGraphtOperationsHelper;
import dev.iq.graph.model.operations.mutable.MutableNodeOperations;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jgrapht.Graph;

/**
 * JGraphT implementation of mutable node operations.
 * Used during graph construction and bulk operations.
 *
 * This implementation ensures referential integrity:
 * - When a node is expired, all connected edges are also expired
 * - When a node is updated, connected edges are recreated to point to the new version
 */
public final class JGraphtMutableNodeOperations implements MutableNodeOperations {

    private final Graph<Node, Edge> graph;
    private final JGraphtMutableEdgeOperations edgeOperations;
    private Map<Locator, Locator> pendingComponentUpdates = new HashMap<>();

    public JGraphtMutableNodeOperations(
            final Graph<Node, Edge> graph, final JGraphtMutableEdgeOperations edgeOperations) {
        this.graph = graph;
        this.edgeOperations = edgeOperations;
    }

    @Override
    public Node add(final Type type, final Data data, final Set<Locator> components, final Instant timestamp) {
        final var locator = Locator.generate();
        final var node = new SimpleNode(locator, type, data, components, timestamp, Optional.empty());
        graph.addVertex(node);
        return node;
    }

    @Override
    public Node update(final NanoId id, final Data data, final Instant timestamp) {
        final var existingNode =
                JGraphtOperationsHelper.validateForExpiry(Versions.findActive(id, graph.vertexSet()), id, "Node");
        return performUpdate(id, existingNode.type(), data, existingNode.components(), timestamp);
    }

    @Override
    public Node update(final NanoId id, final Type type, final Data data, final Instant timestamp) {
        final var existingNode =
                JGraphtOperationsHelper.validateForExpiry(Versions.findActive(id, graph.vertexSet()), id, "Node");
        return performUpdate(id, type, data, existingNode.components(), timestamp);
    }

    @Override
    public Node updateComponents(final NanoId id, final Set<Locator> components, final Instant timestamp) {
        final var existingNode =
                JGraphtOperationsHelper.validateForExpiry(Versions.findActive(id, graph.vertexSet()), id, "Node");
        return performUpdate(id, existingNode.type(), existingNode.data(), components, timestamp);
    }

    private Node performUpdate(
            final NanoId id, final Type type, final Data data, final Set<Locator> components, final Instant timestamp) {
        final var existingNode =
                JGraphtOperationsHelper.validateForExpiry(Versions.findActive(id, graph.vertexSet()), id, "Node");

        // Collect edge information before expiring
        final var edgeRecreationInfo = collectActiveEdgeInfo(existingNode);

        // Expire the existing node (which also expires its edges)
        final var expired = expire(id, timestamp);

        // Create new version
        final var incremented = expired.locator().increment();
        final var newNode = new SimpleNode(incremented, type, data, components, timestamp, Optional.empty());
        graph.addVertex(newNode);

        // Recreate edges to the new node
        recreateEdgesForNode(newNode, edgeRecreationInfo, timestamp);

        return newNode;
    }

    @Override
    public Node expire(final NanoId id, final Instant timestamp) {
        final var node =
                JGraphtOperationsHelper.validateForExpiry(Versions.findActive(id, graph.vertexSet()), id, "Node");

        // Collect all connected edges
        final var allConnectedEdges = collectAllConnectedEdges(node);

        // Expire active edges to maintain referential integrity
        expireActiveEdges(allConnectedEdges, timestamp);

        // Create expired node
        final var expiredNode = new SimpleNode(
                node.locator(), node.type(), node.data(), node.components(), node.created(), Optional.of(timestamp));

        // Remove old node and add expired version
        graph.removeVertex(node); // This removes all connected edges from the graph
        graph.addVertex(expiredNode);

        // Recreate all edges with updated endpoints (pointing to the expired node)
        recreateEdgesAfterNodeExpiry(expiredNode, node, allConnectedEdges, timestamp);

        return expiredNode;
    }

    /**
     * Collects information about active edges connected to a node.
     * This is used when updating a node to recreate the edges.
     */
    private List<EdgeRecreationInfo> collectActiveEdgeInfo(final Node node) {
        final var incomingEdgeInfo = graph.incomingEdgesOf(node).stream()
                .filter(edge -> edge.expired().isEmpty())
                .map(edge -> new EdgeRecreationInfo(
                        edge.locator().id(), edge.type(), edge.source(), node, edge.data(), edge.components(), true));

        final var outgoingEdgeInfo = graph.outgoingEdgesOf(node).stream()
                .filter(edge -> edge.expired().isEmpty())
                .map(edge -> new EdgeRecreationInfo(
                        edge.locator().id(), edge.type(), node, edge.target(), edge.data(), edge.components(), false));

        return Stream.concat(incomingEdgeInfo, outgoingEdgeInfo).toList();
    }

    /**
     * Collects all edges connected to a node (both active and expired).
     */
    private List<Edge> collectAllConnectedEdges(final Node node) {
        final var allConnectedEdges = new ArrayList<Edge>();
        allConnectedEdges.addAll(graph.incomingEdgesOf(node));
        allConnectedEdges.addAll(graph.outgoingEdgesOf(node));
        return allConnectedEdges;
    }

    /**
     * Expires all active edges in the collection.
     */
    private void expireActiveEdges(final Collection<Edge> edges, final Instant timestamp) {
        edges.stream()
                .filter(edge -> edge.expired().isEmpty())
                .forEach(edge -> edgeOperations.expire(edge.locator().id(), timestamp));
    }

    /**
     * Recreates edges for a new node version after update.
     */
    private void recreateEdgesForNode(
            final Node newNode, final Iterable<EdgeRecreationInfo> edgeInfo, final Instant timestamp) {
        edgeInfo.forEach(info -> {
            // Apply any pending component updates to the edge components
            final Set<Locator> updatedComponents = applyPendingComponentUpdates(info.components());

            if (info.incoming()) {
                edgeOperations.add(info.type(), info.source(), newNode, info.data(), updatedComponents, timestamp);
            } else {
                edgeOperations.add(info.type(), newNode, info.target(), info.data(), updatedComponents, timestamp);
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

    /**
     * Sets pending component updates to be applied during edge recreation.
     * This is used by the component operations to ensure edges get updated component references.
     */
    public void setPendingComponentUpdates(final Map<Locator, Locator> updates) {
        this.pendingComponentUpdates = new HashMap<>(updates);
    }

    /**
     * Clears pending component updates.
     */
    public void clearPendingComponentUpdates() {
        this.pendingComponentUpdates.clear();
    }

    /**
     * Recreates edges after a node has been expired.
     * All edges are recreated in their expired state pointing to the expired node.
     */
    private void recreateEdgesAfterNodeExpiry(
            final Node expiredNode,
            final Node originalNode,
            final Iterable<Edge> allConnectedEdges,
            final Instant timestamp) {
        allConnectedEdges.forEach(edge -> {
            // Update the source/target to point to the expired node if it was the original node
            final var source = edge.source().equals(originalNode) ? expiredNode : edge.source();
            final var target = edge.target().equals(originalNode) ? expiredNode : edge.target();

            // If this edge was active, it's now expired; if already expired, keep its expiry time
            final var expiredTime = edge.expired().orElse(timestamp);

            final var recreatedEdge = new SimpleEdge(
                    edge.locator(),
                    edge.type(),
                    source,
                    target,
                    edge.data(),
                    edge.components(),
                    edge.created(),
                    Optional.of(expiredTime));
            graph.addEdge(source, target, recreatedEdge);
        });
    }

    /**
     * Helper record to store edge information for recreation.
     */
    private record EdgeRecreationInfo(
            NanoId id, Type type, Node source, Node target, Data data, Set<Locator> components, boolean incoming) {}
}
