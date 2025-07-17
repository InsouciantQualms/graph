/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import dev.iq.common.persist.SessionExecutor;
import dev.iq.common.persist.SessionFactory;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.api.NodeService;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.jgrapht.NodeOperations;
import dev.iq.graph.persistence.GraphRepository;

/**
 * Default implementation of NodeService using session-based transactions.
 */
public final class DefaultNodeService implements NodeService {

    private final SessionFactory sessionFactory;
    private final GraphRepository repository;
    private final NodeOperations nodeOperations;

    public DefaultNodeService(
            final SessionFactory sessionFactory,
            final GraphRepository repository,
            final NodeOperations nodeOperations) {

        this.sessionFactory = sessionFactory;
        this.repository = repository;
        this.nodeOperations = nodeOperations;
    }

    @Override
    public Node add(final Data data) {

        return SessionExecutor.execute(sessionFactory, () -> {
            final var node = nodeOperations.add(data, Instant.now());
            return repository.nodes().save(node);
        });
    }

    @Override
    public Node update(final NanoId id, final Data data) {

        return SessionExecutor.execute(sessionFactory, () -> {
            final var node = nodeOperations.update(id, data, Instant.now());
            // Save the new version and any modified edges
            repository.nodes().save(node);
            return node;
        });
    }

    @Override
    public List<Node> getNeighbors(final NanoId nodeId) {

        try (final var session = sessionFactory.create()) {
            final var node = nodeOperations
                    .findActive(nodeId)
                    .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
            return nodeOperations.getNeighbors(node);
        }
    }

    @Override
    public Node find(final Locator locator) {
        try (final var session = sessionFactory.create()) {
            return repository
                    .nodes()
                    .find(locator)
                    .orElseThrow(() -> new IllegalArgumentException("Node not found: " + locator));
        }
    }

    @Override
    public Optional<Node> findActive(final NanoId id) {
        try (final var session = sessionFactory.create()) {
            return repository.nodes().findActive(id);
        }
    }

    @Override
    public Optional<Node> findAt(final NanoId id, final Instant timestamp) {
        try (final var session = sessionFactory.create()) {
            return repository.nodes().findAt(id, timestamp);
        }
    }

    @Override
    public List<Node> findAllVersions(final NanoId id) {
        try (final var session = sessionFactory.create()) {
            return repository.nodes().findAll(id);
        }
    }

    @Override
    public List<NanoId> allActive() {
        try (final var session = sessionFactory.create()) {
            // This is a simplified implementation - in reality would need a more efficient approach
            return all().stream().filter(id -> findActive(id).isPresent()).collect(Collectors.toList());
        }
    }

    @Override
    public List<NanoId> all() {
        try (final var session = sessionFactory.create()) {
            // TODO: This requires repository enhancement to get all node IDs
            throw new UnsupportedOperationException(
                    "Not yet implemented - requires repository enhancement to get all node IDs");
        }
    }

    @Override
    public Optional<Node> expire(final NanoId id) {

        return SessionExecutor.execute(sessionFactory, () -> {
            final var activeNode = nodeOperations.findActive(id);
            if (activeNode.isPresent()) {
                final var expired = nodeOperations.expire(id, Instant.now());
                repository.nodes().expire(id, expired.expired().get());
                return Optional.of(expired);
            }
            return Optional.empty();
        });
    }

    @Override
    public boolean delete(final NanoId id) {
        return SessionExecutor.execute(sessionFactory, () -> repository.nodes().delete(id));
    }
}
