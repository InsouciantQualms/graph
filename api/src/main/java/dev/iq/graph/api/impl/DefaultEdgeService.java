/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api.impl;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.api.EdgeService;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.GraphSpace;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.View;
import dev.iq.graph.model.jgrapht.JGraphtGraphBuilder;
import dev.iq.graph.model.operations.EdgeOperations;
import dev.iq.graph.model.simple.SimpleType;
import dev.iq.graph.persistence.GraphRepository;
import dev.iq.graph.persistence.Session;
import dev.iq.graph.persistence.SessionFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Default implementation of EdgeService that delegates to persistence layer and model operations.
 */
public final class DefaultEdgeService implements EdgeService {

    private final GraphRepository graphRepository;
    private final SessionFactory sessionFactory;
    private final EdgeOperations edgeOperations;

    public DefaultEdgeService(
            final GraphRepository graphRepository,
            final SessionFactory sessionFactory,
            final EdgeOperations edgeOperations) {
        this.graphRepository = graphRepository;
        this.sessionFactory = sessionFactory;
        this.edgeOperations = edgeOperations;
    }

    @Override
    public Edge addEdge(final Node source, final Node target, final Data data) {
        try (final Session session = sessionFactory.create()) {
            final Edge edge = edgeOperations.add(
                    new SimpleType("EDGE"),
                    source,
                    target,
                    data,
                    new HashSet<>(), // Empty set of components for now
                    Instant.now());
            session.commit();
            return edge;
        } catch (Exception e) {
            throw new RuntimeException("Failed to add edge", e);
        }
    }

    @Override
    public Edge updateEdge(final NanoId id, final Data data) {
        try (final Session session = sessionFactory.create()) {
            // Find the existing edge to get its type and components
            final Optional<Edge> existingEdge = graphRepository.edges().findActive(id);
            if (existingEdge.isEmpty()) {
                throw new IllegalArgumentException("Edge not found: " + id);
            }

            final Edge updatedEdge = edgeOperations.update(
                    id, existingEdge.get().type(), data, existingEdge.get().components(), Instant.now());
            session.commit();
            return updatedEdge;
        } catch (Exception e) {
            throw new RuntimeException("Failed to update edge", e);
        }
    }

    @Override
    public List<Edge> getEdgesFrom(final NanoId nodeId) {
        final GraphSpace graphSpace = buildGraphSpace();
        final View view = graphSpace.view();

        final Optional<Node> node = view.nodes().findActive(nodeId);
        if (node.isEmpty()) {
            return new ArrayList<>();
        }

        final Set<Edge> outgoingEdges = view.outgoingEdges(node.get());
        return new ArrayList<>(outgoingEdges);
    }

    @Override
    public List<Edge> getEdgesTo(final NanoId nodeId) {
        final GraphSpace graphSpace = buildGraphSpace();
        final View view = graphSpace.view();

        final Optional<Node> node = view.nodes().findActive(nodeId);
        if (node.isEmpty()) {
            return new ArrayList<>();
        }

        final Set<Edge> incomingEdges = view.incomingEdges(node.get());
        return new ArrayList<>(incomingEdges);
    }

    @Override
    public Edge find(final Locator locator) {
        final Edge edge = graphRepository.edges().find(locator);
        if (edge == null) {
            throw new IllegalArgumentException("Edge not found: " + locator);
        }
        return edge;
    }

    @Override
    public Optional<Edge> findActive(final NanoId id) {
        return graphRepository.edges().findActive(id);
    }

    @Override
    public Optional<Edge> findAt(final NanoId id, final Instant timestamp) {
        return graphRepository.edges().findAt(id, timestamp);
    }

    @Override
    public List<Edge> findVersions(final NanoId id) {
        return graphRepository.edges().findVersions(id);
    }

    @Override
    public List<NanoId> allActive() {
        return graphRepository.edges().allActiveIds();
    }

    @Override
    public List<NanoId> all() {
        return graphRepository.edges().allIds();
    }

    @Override
    public Optional<Edge> expire(final NanoId id) {
        try (final Session session = sessionFactory.create()) {
            final Optional<Edge> edge = graphRepository.edges().findActive(id);
            if (edge.isPresent()) {
                final Edge expiredEdge = edgeOperations.expire(id, Instant.now());
                session.commit();
                return Optional.of(expiredEdge);
            }
            return Optional.empty();
        } catch (Exception e) {
            throw new RuntimeException("Failed to expire edge", e);
        }
    }

    @Override
    public boolean delete(final NanoId id) {
        try (final Session session = sessionFactory.create()) {
            final boolean deleted = graphRepository.edges().delete(id);
            session.commit();
            return deleted;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete edge", e);
        }
    }

    private GraphSpace buildGraphSpace() {
        // Build the graph from persistence layer
        final JGraphtGraphBuilder builder = new JGraphtGraphBuilder();

        // Load all active nodes
        graphRepository.nodes().allActiveIds().forEach(nodeId -> {
            graphRepository.nodes().findActive(nodeId).ifPresent(node -> {
                builder.addNode(node.locator().id(), node.type(), node.data(), node.created());
            });
        });

        // Load all active edges
        graphRepository.edges().allActiveIds().forEach(edgeId -> {
            graphRepository.edges().findActive(edgeId).ifPresent(edge -> {
                builder.addEdge(
                        edge.type(), edge.source(), edge.target(), edge.data(), edge.components(), edge.created());
            });
        });

        return builder.build();
    }
}
