/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api.impl;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.api.NodeService;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.GraphSpace;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.View;
import dev.iq.graph.model.jgrapht.JGraphtGraphBuilder;
import dev.iq.graph.model.operations.NodeOperations;
import dev.iq.graph.model.simple.SimpleType;
import dev.iq.graph.persistence.GraphRepository;
import dev.iq.graph.persistence.Session;
import dev.iq.graph.persistence.SessionFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Default implementation of NodeService that delegates to persistence layer and model operations.
 */
public final class DefaultNodeService implements NodeService {

    private final GraphRepository graphRepository;
    private final SessionFactory sessionFactory;
    private final NodeOperations nodeOperations;

    public DefaultNodeService(
            final GraphRepository graphRepository,
            final SessionFactory sessionFactory,
            final NodeOperations nodeOperations) {
        this.graphRepository = graphRepository;
        this.sessionFactory = sessionFactory;
        this.nodeOperations = nodeOperations;
    }

    @Override
    public Node add(final Data data) {
        try (final Session session = sessionFactory.create()) {
            final Node node = nodeOperations.add(Locator.generate().id(), new SimpleType("NODE"), data, Instant.now());
            session.commit();
            return node;
        } catch (Exception e) {
            throw new RuntimeException("Failed to add node", e);
        }
    }

    @Override
    public Node update(final NanoId id, final Data data) {
        try (final Session session = sessionFactory.create()) {
            // Find the existing node to get its type
            final Optional<Node> existingNode = graphRepository.nodes().findActive(id);
            if (existingNode.isEmpty()) {
                throw new IllegalArgumentException("Node not found: " + id);
            }

            final Node updatedNode =
                    nodeOperations.update(id, existingNode.get().type(), data, Instant.now());
            session.commit();
            return updatedNode;
        } catch (Exception e) {
            throw new RuntimeException("Failed to update node", e);
        }
    }

    @Override
    public List<Node> getNeighbors(final NanoId nodeId) {
        final GraphSpace graphSpace = buildGraphSpace();
        final View view = graphSpace.view();

        final Optional<Node> node = view.nodes().findActive(nodeId);
        if (node.isEmpty()) {
            return new ArrayList<>();
        }

        final Set<Node> neighbors = view.neighbors(node.get());
        return new ArrayList<>(neighbors);
    }

    @Override
    public Node find(final Locator locator) {
        final Node node = graphRepository.nodes().find(locator);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + locator);
        }
        return node;
    }

    @Override
    public Optional<Node> findActive(final NanoId id) {
        return graphRepository.nodes().findActive(id);
    }

    @Override
    public Optional<Node> findAt(final NanoId id, final Instant timestamp) {
        return graphRepository.nodes().findAt(id, timestamp);
    }

    @Override
    public List<Node> findVersions(final NanoId id) {
        return graphRepository.nodes().findVersions(id);
    }

    @Override
    public List<NanoId> allActive() {
        return graphRepository.nodes().allActiveIds();
    }

    @Override
    public List<NanoId> all() {
        return graphRepository.nodes().allIds();
    }

    @Override
    public Optional<Node> expire(final NanoId id) {
        try (final Session session = sessionFactory.create()) {
            final Optional<Node> node = graphRepository.nodes().findActive(id);
            if (node.isPresent()) {
                final Node expiredNode = nodeOperations.expire(id, Instant.now());
                session.commit();
                return Optional.of(expiredNode);
            }
            return Optional.empty();
        } catch (Exception e) {
            throw new RuntimeException("Failed to expire node", e);
        }
    }

    @Override
    public boolean delete(final NanoId id) {
        try (final Session session = sessionFactory.create()) {
            final boolean deleted = graphRepository.nodes().delete(id);
            session.commit();
            return deleted;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete node", e);
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
