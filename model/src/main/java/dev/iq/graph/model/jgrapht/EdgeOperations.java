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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.jgrapht.Graph;

/**
 * JGraphT-based implementation of edge operations for versioned graph elements.
 */
public final class EdgeOperations implements Operations<Edge> {

    private final Graph<Reference<Node>, Reference<Edge>> graph;

    public EdgeOperations(final Graph<Reference<Node>, Reference<Edge>> graph) {

        this.graph = graph;
    }

    public Set<Edge> outgoingEdgesOf(final Node node) {
        final Reference<Node> nodeRef = new Reference.Loaded<>(node);
        final Set<Edge> edges = new HashSet<>();
        for (Reference<Edge> ref : graph.outgoingEdgesOf(nodeRef)) {
            if (ref instanceof Reference.Loaded<Edge> loaded) {
                edges.add(loaded.value());
            }
        }
        return edges;
    }

    public Set<Edge> incomingEdgesOf(final Node node) {
        final Reference<Node> nodeRef = new Reference.Loaded<>(node);
        final Set<Edge> edges = new HashSet<>();
        for (Reference<Edge> ref : graph.incomingEdgesOf(nodeRef)) {
            if (ref instanceof Reference.Loaded<Edge> loaded) {
                edges.add(loaded.value());
            }
        }
        return edges;
    }

    public Edge add(final Node source, final Node target, final Data data, final Instant timestamp) {
        final var locator = Locator.generate();
        // Create edge with loaded source and target references
        final Reference<Node> sourceRef = new Reference.Loaded<>(source);
        final Reference<Node> targetRef = new Reference.Loaded<>(target);
        final var edge = new SimpleEdge(locator, sourceRef, targetRef, data, timestamp, Optional.empty());
        final Reference<Edge> edgeRef = new Reference.Loaded<>(edge);
        graph.addEdge(sourceRef, targetRef, edgeRef);
        return edge;
    }

    public Edge update(final NanoId id, final Data data, final Instant timestamp) {

        final var expired = expire(id, timestamp);
        final var incremented = expired.locator().increment();
        final var newEdge =
                new SimpleEdge(incremented, expired.source(), expired.target(), data, timestamp, Optional.empty());
        final Reference<Edge> newEdgeRef = new Reference.Loaded<>(newEdge);
        graph.addEdge(expired.source(), expired.target(), newEdgeRef);
        return newEdge;
    }

    @Override
    public Optional<Edge> findActive(final NanoId id) {
        // Extract edges from loaded references
        final List<Edge> edges = new ArrayList<>();
        for (Reference<Edge> ref : graph.edgeSet()) {
            if (ref instanceof Reference.Loaded<Edge> loaded) {
                edges.add(loaded.value());
            }
        }
        return Versions.findActive(id, edges);
    }

    @Override
    public Optional<Edge> findAt(final NanoId id, final Instant timestamp) {
        // Extract edges from loaded references
        final List<Edge> edges = new ArrayList<>();
        for (Reference<Edge> ref : graph.edgeSet()) {
            if (ref instanceof Reference.Loaded<Edge> loaded) {
                edges.add(loaded.value());
            }
        }
        return Versions.findAt(id, timestamp, edges);
    }

    @Override
    public List<Edge> findVersions(final NanoId id) {
        // Extract edges from loaded references
        final List<Edge> edges = new ArrayList<>();
        for (Reference<Edge> ref : graph.edgeSet()) {
            if (ref instanceof Reference.Loaded<Edge> loaded) {
                edges.add(loaded.value());
            }
        }
        return Versions.findAllVersions(id, edges);
    }

    @Override
    public Edge find(final Locator locator) {
        for (Reference<Edge> ref : graph.edgeSet()) {
            if (ref instanceof Reference.Loaded<Edge> loaded
                    && loaded.value().locator().equals(locator)) {
                return loaded.value();
            }
        }
        throw new NoSuchElementException("Edge not found: " + locator);
    }

    @Override
    public Edge expire(final NanoId id, final Instant timestamp) {

        final var edge = OperationsHelper.validateForExpiry(findActive(id), id, "Edge");
        final var expiredEdge = new SimpleEdge(
                edge.locator(), edge.source(), edge.target(), edge.data(), edge.created(), Optional.of(timestamp));

        // Find and remove the old edge reference
        Reference<Edge> oldEdgeRef = null;
        for (Reference<Edge> ref : graph.edgeSet()) {
            if (ref instanceof Reference.Loaded<Edge> loaded && loaded.value().equals(edge)) {
                oldEdgeRef = ref;
                break;
            }
        }

        if (oldEdgeRef != null) {
            graph.removeEdge(oldEdgeRef);
        }

        // Add the expired edge
        final Reference<Edge> expiredEdgeRef = new Reference.Loaded<>(expiredEdge);
        graph.addEdge(edge.source(), edge.target(), expiredEdgeRef);

        return expiredEdge;
    }

    /**
     * Gets all active edges originating from the specified node.
     */
    public List<Edge> getEdgesFrom(final Node node) {
        return outgoingEdgesOf(node).stream()
                .filter(edge -> edge.expired().isEmpty())
                .toList();
    }

    /**
     * Gets all active edges terminating at the specified node.
     */
    public List<Edge> getEdgesTo(final Node node) {
        return incomingEdgesOf(node).stream()
                .filter(edge -> edge.expired().isEmpty())
                .toList();
    }

    /**
     * Gets all active edges (both incoming and outgoing) for the specified node.
     */
    public List<Edge> getEdgesFor(final Node node) {
        final var outgoing = getEdgesFrom(node);
        final var incoming = getEdgesTo(node);
        final var allEdges = new ArrayList<Edge>(outgoing.size() + incoming.size());
        allEdges.addAll(outgoing);
        allEdges.addAll(incoming);
        return allEdges;
    }
}
