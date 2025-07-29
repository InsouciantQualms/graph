/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.common.version.Versions;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Operations;
import dev.iq.graph.model.Reference;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jgrapht.Graph;

/**
 * JGraphT-based implementation of node operations for versioned graph elements.
 */
public final class NodeOperations implements Operations<Node> {

    private final Graph<Reference<Node>, Reference<Edge>> graph;

    private final EdgeOperations edgeDelegate;

    public NodeOperations(final Graph<Reference<Node>, Reference<Edge>> graph, final EdgeOperations edgeDelegate) {

        this.graph = graph;
        this.edgeDelegate = edgeDelegate;
    }

    public boolean contains(final Node node) {
        final Reference<Node> ref = new Reference.Loaded<>(node);
        return graph.containsVertex(ref);
    }

    public Set<Node> vertexSet() {
        final Set<Node> nodes = new HashSet<>();
        for (Reference<Node> ref : graph.vertexSet()) {
            if (ref instanceof Reference.Loaded<Node> loaded) {
                nodes.add(loaded.value());
            }
        }
        return nodes;
    }

    public Node add(final Data data, final Instant timestamp) {

        final var locator = Locator.generate();
        // Create node with empty edge references (unloaded)
        final var node = new SimpleNode(locator, new ArrayList<>(), data, timestamp, Optional.empty());
        final Reference<Node> nodeRef = new Reference.Loaded<>(node);
        graph.addVertex(nodeRef);
        return node;
    }

    public Node update(final NanoId id, final Data data, final Instant timestamp) {

        final var existingNode = OperationsHelper.validateForExpiry(findActive(id), id, "Node");

        // Collect edge information before expiring
        final var edgeRecreationInfo = collectActiveEdgeInfo(existingNode);

        // Expire the existing node and its edges
        final var expired = expire(id, timestamp);

        // Create new version
        final var incremented = expired.locator().increment();
        final var newNode = new SimpleNode(incremented, new ArrayList<>(), data, timestamp, Optional.empty());
        final Reference<Node> newNodeRef = new Reference.Loaded<>(newNode);
        graph.addVertex(newNodeRef);

        // Recreate edges to the new node
        recreateEdgesForNode(newNode, edgeRecreationInfo, timestamp);

        return newNode;
    }

    @Override
    public Optional<Node> findActive(final NanoId id) {
        // Extract nodes from loaded references
        final List<Node> nodes = new ArrayList<>();
        for (Reference<Node> ref : graph.vertexSet()) {
            if (ref instanceof Reference.Loaded<Node> loaded) {
                nodes.add(loaded.value());
            }
        }
        return Versions.findActive(id, nodes);
    }

    @Override
    public Optional<Node> findAt(final NanoId id, final Instant timestamp) {
        // Extract nodes from loaded references
        final List<Node> nodes = new ArrayList<>();
        for (Reference<Node> ref : graph.vertexSet()) {
            if (ref instanceof Reference.Loaded<Node> loaded) {
                nodes.add(loaded.value());
            }
        }
        return Versions.findAt(id, timestamp, nodes);
    }

    @Override
    public List<Node> findVersions(final NanoId id) {
        // Extract nodes from loaded references
        final List<Node> nodes = new ArrayList<>();
        for (Reference<Node> ref : graph.vertexSet()) {
            if (ref instanceof Reference.Loaded<Node> loaded) {
                nodes.add(loaded.value());
            }
        }
        return Versions.findAllVersions(id, nodes);
    }

    @Override
    public Node find(final Locator locator) {
        for (Reference<Node> ref : graph.vertexSet()) {
            if (ref instanceof Reference.Loaded<Node> loaded
                    && loaded.value().locator().equals(locator)) {
                return loaded.value();
            }
        }
        throw new NoSuchElementException("Node not found: " + locator);
    }

    public Optional<Node> findNodeAt(final NanoId id, final Instant timestamp) {
        return findAt(id, timestamp);
    }

    public List<Node> activeNodes() {
        final List<Node> nodes = new ArrayList<>();
        for (Reference<Node> ref : graph.vertexSet()) {
            if (ref instanceof Reference.Loaded<Node> loaded) {
                nodes.add(loaded.value());
            }
        }
        return Versions.allActive(nodes);
    }

    /**
     * Gets all neighbor nodes connected to the specified node via active edges.
     */
    public List<Node> getNeighbors(final Node node) {
        final Reference<Node> nodeRef = new Reference.Loaded<>(node);

        final var outgoingNeighbors = graph.outgoingEdgesOf(nodeRef).stream()
                .filter(edgeRef -> edgeRef instanceof Reference.Loaded<Edge> loaded
                        && loaded.value().expired().isEmpty())
                .map(edgeRef -> ((Reference.Loaded<Edge>) edgeRef).value().target())
                .filter(targetRef -> targetRef instanceof Reference.Loaded<Node>)
                .map(targetRef -> ((Reference.Loaded<Node>) targetRef).value());

        final var incomingNeighbors = graph.incomingEdgesOf(nodeRef).stream()
                .filter(edgeRef -> edgeRef instanceof Reference.Loaded<Edge> loaded
                        && loaded.value().expired().isEmpty())
                .map(edgeRef -> ((Reference.Loaded<Edge>) edgeRef).value().source())
                .filter(sourceRef -> sourceRef instanceof Reference.Loaded<Node>)
                .map(sourceRef -> ((Reference.Loaded<Node>) sourceRef).value());

        return Stream.concat(outgoingNeighbors, incomingNeighbors).toList();
    }

    @Override
    public Node expire(final NanoId id, final Instant timestamp) {

        final var node = OperationsHelper.validateForExpiry(findActive(id), id, "Node");
        final Reference<Node> nodeRef = new Reference.Loaded<>(node);

        // Collect all connected edges
        final var allConnectedEdges = collectAllConnectedEdges(nodeRef);

        // Expire active edges
        expireActiveEdges(allConnectedEdges, timestamp);

        // Create expired node with unloaded edge references
        final List<Reference<Edge>> unloadedEdgeRefs = node.edges().stream()
                .map(edgeRef -> {
                    if (edgeRef instanceof Reference.Loaded<Edge> loaded) {
                        return (Reference<Edge>)
                                new Reference.Unloaded<>(loaded.value().locator(), Edge.class);
                    }
                    return edgeRef;
                })
                .toList();

        final var expiredNode = new SimpleNode(
                node.locator(),
                node.type(),
                unloadedEdgeRefs,
                node.data(),
                node.created(),
                Optional.of(timestamp),
                node.components());

        // Remove old node and add expired version
        graph.removeVertex(nodeRef); // This removes all connected edges
        final Reference<Node> expiredNodeRef = new Reference.Loaded<>(expiredNode);
        graph.addVertex(expiredNodeRef);

        // Recreate all edges with updated endpoints
        recreateEdgesAfterNodeExpiry(expiredNodeRef, nodeRef, allConnectedEdges, timestamp);

        return expiredNode;
    }

    /**
     * Collects information about active edges connected to a node.
     */
    private List<EdgeRecreationInfo> collectActiveEdgeInfo(final Node node) {
        final Reference<Node> nodeRef = new Reference.Loaded<>(node);

        final var incomingEdgeInfo = graph.incomingEdgesOf(nodeRef).stream()
                .filter(edgeRef -> edgeRef instanceof Reference.Loaded<Edge> loaded
                        && loaded.value().expired().isEmpty())
                .map(edgeRef -> {
                    Edge edge = ((Reference.Loaded<Edge>) edgeRef).value();
                    return new EdgeRecreationInfo(edge.source(), nodeRef, edge.data(), true);
                });

        final var outgoingEdgeInfo = graph.outgoingEdgesOf(nodeRef).stream()
                .filter(edgeRef -> edgeRef instanceof Reference.Loaded<Edge> loaded
                        && loaded.value().expired().isEmpty())
                .map(edgeRef -> {
                    Edge edge = ((Reference.Loaded<Edge>) edgeRef).value();
                    return new EdgeRecreationInfo(nodeRef, edge.target(), edge.data(), false);
                });

        return Stream.concat(incomingEdgeInfo, outgoingEdgeInfo).toList();
    }

    /**
     * Collects all edges connected to a node.
     */
    private List<Reference<Edge>> collectAllConnectedEdges(final Reference<Node> nodeRef) {

        final var allConnectedEdges = new ArrayList<Reference<Edge>>();
        allConnectedEdges.addAll(graph.incomingEdgesOf(nodeRef));
        allConnectedEdges.addAll(graph.outgoingEdgesOf(nodeRef));
        return allConnectedEdges;
    }

    /**
     * Expires all active edges in the given collection.
     */
    private void expireActiveEdges(final Collection<Reference<Edge>> edges, final Instant timestamp) {

        edges.stream()
                .filter(edgeRef -> edgeRef instanceof Reference.Loaded<Edge> loaded
                        && loaded.value().expired().isEmpty())
                .forEach(edgeRef -> {
                    Edge edge = ((Reference.Loaded<Edge>) edgeRef).value();
                    edgeDelegate.expire(edge.locator().id(), timestamp);
                });
    }

    /**
     * Recreates edges for a new node version.
     */
    private void recreateEdgesForNode(
            final Node newNode, final Iterable<EdgeRecreationInfo> edgeInfo, final Instant timestamp) {

        edgeInfo.forEach(info -> {
            if (info.incoming()) {
                // Extract source node from reference
                if (info.source() instanceof Reference.Loaded<Node> loaded) {
                    edgeDelegate.add(loaded.value(), newNode, info.data(), timestamp);
                }
            } else {
                // Extract target node from reference
                if (info.target() instanceof Reference.Loaded<Node> loaded) {
                    edgeDelegate.add(newNode, loaded.value(), info.data(), timestamp);
                }
            }
        });
    }

    /**
     * Recreates edges after a node has been expired.
     */
    private void recreateEdgesAfterNodeExpiry(
            final Reference<Node> expiredNodeRef,
            final Reference<Node> originalNodeRef,
            final Iterable<Reference<Edge>> allConnectedEdges,
            final Instant timestamp) {

        allConnectedEdges.forEach(edgeRef -> {
            if (edgeRef instanceof Reference.Loaded<Edge> loaded) {
                Edge edge = loaded.value();

                // Update the source/target to point to the expired node if it was the original node
                final var source = edge.source().equals(originalNodeRef) ? expiredNodeRef : edge.source();
                final var target = edge.target().equals(originalNodeRef) ? expiredNodeRef : edge.target();

                // If this edge was active, expire it; if it was already expired, keep its expiry time
                final var expiredTime =
                        edge.expired().isPresent() ? edge.expired().get() : timestamp;

                final var recreatedEdge = new SimpleEdge(
                        edge.locator(),
                        edge.type(),
                        source,
                        target,
                        edge.data(),
                        edge.created(),
                        Optional.of(expiredTime),
                        edge.components());

                final Reference<Edge> recreatedEdgeRef = new Reference.Loaded<>(recreatedEdge);
                graph.addEdge(source, target, recreatedEdgeRef);
            }
        });
    }

    /**
     * Helper record to store edge information for recreation.
     */
    private record EdgeRecreationInfo(Reference<Node> source, Reference<Node> target, Data data, boolean incoming) {}
}
