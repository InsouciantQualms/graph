/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api.impl;

import dev.iq.common.persist.SessionExecutor;
import dev.iq.common.persist.SessionFactory;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.api.EdgeService;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.jgrapht.EdgeOperations;
import dev.iq.graph.persistence.GraphRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Default implementation of EdgeService using session-based transactions.
 */
public final class DefaultEdgeService implements EdgeService {

    private final SessionFactory sessionFactory;
    private final GraphRepository repository;
    private final EdgeOperations edgeOperations;

    public DefaultEdgeService(
            final SessionFactory sessionFactory,
            final GraphRepository repository,
            final EdgeOperations edgeOperations) {

        this.sessionFactory = sessionFactory;
        this.repository = repository;
        this.edgeOperations = edgeOperations;
    }

    @Override
    public Edge addEdge(final Node source, final Node target, final Data data) {

        return SessionExecutor.execute(sessionFactory, () -> {
            final var edge = edgeOperations.add(source, target, data, Instant.now());
            return repository.edges().save(edge);
        });
    }

    @Override
    public Edge updateEdge(final NanoId id, final Data data) {

        return SessionExecutor.execute(sessionFactory, () -> {
            final var edge = edgeOperations.update(id, data, Instant.now());
            repository.edges().save(edge);
            return edge;
        });
    }

    @Override
    public List<Edge> getEdgesFrom(final NanoId nodeId) {

        try (final var session = sessionFactory.create()) {
            // Need NodeOperations to find the node in the graph
            // This indicates we need better integration between services and operations
            // For now, return empty list as operations are not properly integrated
            return List.of();
        }
    }

    @Override
    public List<Edge> getEdgesTo(final NanoId nodeId) {

        try (final var session = sessionFactory.create()) {
            // Need NodeOperations to find the node in the graph
            // This indicates we need better integration between services and operations
            // For now, return empty list as operations are not properly integrated
            return List.of();
        }
    }

    @Override
    public Edge find(final Locator locator) {

        try (final var session = sessionFactory.create()) {
            return repository
                    .edges()
                    .find(locator)
                    .orElseThrow(() -> new IllegalArgumentException("Edge not found: " + locator));
        }
    }

    @Override
    public Optional<Edge> findActive(final NanoId id) {

        try (final var session = sessionFactory.create()) {
            return repository.edges().findActive(id);
        }
    }

    @Override
    public Optional<Edge> findAt(final NanoId id, final Instant timestamp) {

        try (final var session = sessionFactory.create()) {
            return repository.edges().findAt(id, timestamp);
        }
    }

    @Override
    public List<Edge> findAllVersions(final NanoId id) {

        try (final var session = sessionFactory.create()) {
            return repository.edges().findAll(id);
        }
    }

    @Override
    public List<NanoId> allActive() {

        try (final var session = sessionFactory.create()) {
            // This would need proper implementation with repository support
            return all().stream().filter(id -> findActive(id).isPresent()).collect(Collectors.toList());
        }
    }

    @Override
    public List<NanoId> all() {

        try (final var session = sessionFactory.create()) {
            // TODO: This requires repository enhancement to get all edge IDs
            throw new UnsupportedOperationException(
                    "Not yet implemented - requires repository enhancement to get all edge IDs");
        }
    }

    @Override
    public Optional<Edge> expire(final NanoId id) {

        return SessionExecutor.execute(sessionFactory, () -> {
            final var activeEdge = edgeOperations.findActive(id);
            if (activeEdge.isPresent()) {
                final var expired = edgeOperations.expire(id, Instant.now());
                repository.edges().expire(id, expired.expired().get());
                return Optional.of(expired);
            }
            return Optional.empty();
        });
    }

    @Override
    public boolean delete(final NanoId id) {

        return SessionExecutor.execute(sessionFactory, () -> repository.edges().delete(id));
    }
}
