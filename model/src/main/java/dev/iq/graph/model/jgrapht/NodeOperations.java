/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import org.jgrapht.Graph;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.common.version.Versions;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Operations;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleNode;

/**
 * JGraphT-based implementation of node operations for versioned graph elements.
 */
public final class NodeOperations implements Operations<Node> {

    private final Graph<Node, Edge> graph;

    private final EdgeOperations edgeDelegate;

    public NodeOperations(final Graph<Node, Edge> graph, final EdgeOperations edgeDelegate) {

        this.graph = graph;
        this.edgeDelegate = edgeDelegate;
    }

    public boolean contains(final Node node) {

        return graph.containsVertex(node);
    }

    public Set<Node> vertexSet() {

        return graph.vertexSet();
    }

    public Node add(final Data data, final Instant timestamp) {

        final var locator = Locator.generate();
        // TODO Should I call EdgeOperations#findEdgesAt here.  If yes, how to avoid fetching whole graph?
        final var node = new SimpleNode(locator, new ArrayList<>(), data, timestamp, Optional.empty());
        graph.addVertex(node);
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
        graph.addVertex(newNode);

        // Recreate edges to the new node
        recreateEdgesForNode(newNode, edgeRecreationInfo, timestamp);

        return newNode;
    }

    @Override
    public Optional<Node> findActive(final NanoId id) {
        return Versions.findActive(id, graph.vertexSet());
    }

    @Override
    public Optional<Node> findAt(final NanoId id, final Instant timestamp) {
        return Versions.findAt(id, timestamp, graph.vertexSet());
    }

    @Override
    public List<Node> findAllVersions(final NanoId id) {
        return Versions.findAllVersions(id, graph.vertexSet());
    }

    @Override
    public List<Node> allActive() {
        return Versions.allActive(graph.vertexSet());
    }

    public Optional<Node> findNodeAt(final NanoId id, final Instant timestamp) {
        return findAt(id, timestamp);
    }

    public List<Node> activeNodes() {
        return allActive();
    }

    /**
     * Gets all neighbor nodes connected to the specified node via active edges.
     */
    public List<Node> getNeighbors(final Node node) {

        final var outgoingNeighbors = graph.outgoingEdgesOf(node).stream()
                .filter(edge -> edge.expired().isEmpty())
                .map(Edge::target);

        final var incomingNeighbors = graph.incomingEdgesOf(node).stream()
                .filter(edge -> edge.expired().isEmpty())
                .map(Edge::source);

        return Stream.concat(outgoingNeighbors, incomingNeighbors).toList();
    }

    @Override
    public Node expire(final NanoId id, final Instant timestamp) {

        final var node = OperationsHelper.validateForExpiry(findActive(id), id, "Node");

        // Collect all connected edges
        final var allConnectedEdges = collectAllConnectedEdges(node);

        // Expire active edges
        expireActiveEdges(allConnectedEdges, timestamp);

        // Create expired node
        final var expiredNode =
                new SimpleNode(node.locator(), node.edges(), node.data(), node.created(), Optional.of(timestamp));

        // Remove old node and add expired version
        graph.removeVertex(node); // This removes all connected edges
        graph.addVertex(expiredNode);

        // Recreate all edges with updated endpoints
        recreateEdgesAfterNodeExpiry(expiredNode, node, allConnectedEdges, timestamp);

        return expiredNode;
    }

    /**
     * Collects information about active edges connected to a node.
     */
    private List<EdgeRecreationInfo> collectActiveEdgeInfo(final Node node) {

        final var incomingEdgeInfo = graph.incomingEdgesOf(node).stream()
                .filter(edge -> edge.expired().isEmpty())
                .map(edge -> new EdgeRecreationInfo(edge.source(), node, edge.data(), true));

        final var outgoingEdgeInfo = graph.outgoingEdgesOf(node).stream()
                .filter(edge -> edge.expired().isEmpty())
                .map(edge -> new EdgeRecreationInfo(node, edge.target(), edge.data(), false));

        return Stream.concat(incomingEdgeInfo, outgoingEdgeInfo).toList();
    }

    /**
     * Collects all edges connected to a node.
     */
    private List<Edge> collectAllConnectedEdges(final Node node) {

        final var allConnectedEdges = new ArrayList<Edge>();
        allConnectedEdges.addAll(graph.incomingEdgesOf(node));
        allConnectedEdges.addAll(graph.outgoingEdgesOf(node));
        return allConnectedEdges;
    }

    /**
     * Expires all active edges in the given collection.
     */
    private void expireActiveEdges(final Collection<Edge> edges, final Instant timestamp) {

        edges.stream()
                .filter(edge -> edge.expired().isEmpty())
                .forEach(edge -> edgeDelegate.expire(edge.locator().id(), timestamp));
    }

    /**
     * Recreates edges for a new node version.
     */
    private void recreateEdgesForNode(
            final Node newNode, final Iterable<EdgeRecreationInfo> edgeInfo, final Instant timestamp) {

        edgeInfo.forEach(info -> {
            if (info.incoming()) {
                edgeDelegate.add(info.source(), newNode, info.data(), timestamp);
            } else {
                edgeDelegate.add(newNode, info.target(), info.data(), timestamp);
            }
        });
    }

    /**
     * Recreates edges after a node has been expired.
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

            // If this edge was active, expire it; if it was already expired, keep its expiry time
            final var expiredTime = edge.expired().isPresent() ? edge.expired().get() : timestamp;

            final var recreatedEdge = new SimpleEdge(
                    edge.locator(), source, target, edge.data(), edge.created(), Optional.of(expiredTime));
            graph.addEdge(source, target, recreatedEdge);
        });
    }

    /**
     * Helper record to store edge information for recreation.
     */
    private record EdgeRecreationInfo(Node source, Node target, Data data, boolean incoming) {}
}
