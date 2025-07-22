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
import dev.iq.graph.model.Node;
import dev.iq.graph.model.jgrapht.EdgeOperations;
import dev.iq.graph.model.jgrapht.NodeOperations;
import dev.iq.graph.persistence.GraphRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of EdgeService using session-based transactions.
 */
@Service
public final class DefaultEdgeService implements EdgeService {

    private final GraphRepository repository;
    private final EdgeOperations edgeOperations;
    private final NodeOperations nodeOperations;

    public DefaultEdgeService(
            final GraphRepository repository,
            final EdgeOperations edgeOperations,
            final NodeOperations nodeOperations) {

        this.repository = repository;
        this.edgeOperations = edgeOperations;
        this.nodeOperations = nodeOperations;
    }

    @Override
    @Transactional
    public Edge addEdge(final Node source, final Node target, final Data data) {

        final var edge = edgeOperations.add(source, target, data, Instant.now());
        return repository.edges().save(edge);
    }

    @Override
    @Transactional
    public Edge updateEdge(final NanoId id, final Data data) {

        final var edge = edgeOperations.update(id, data, Instant.now());
        repository.edges().save(edge);
        return edge;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Edge> getEdgesFrom(final NanoId nodeId) {

        final var node = nodeOperations
                .findActive(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
        return edgeOperations.getEdgesFrom(node);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Edge> getEdgesTo(final NanoId nodeId) {

        final var node = nodeOperations
                .findActive(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
        return edgeOperations.getEdgesTo(node);
    }

    @Override
    @Transactional(readOnly = true)
    public Edge find(final Locator locator) {

        return repository
                .edges()
                .find(locator)
                .orElseThrow(() -> new IllegalArgumentException("Edge not found: " + locator));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Edge> findActive(final NanoId id) {

        return repository.edges().findActive(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Edge> findAt(final NanoId id, final Instant timestamp) {

        return repository.edges().findAt(id, timestamp);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Edge> findAllVersions(final NanoId id) {

        return repository.edges().findAll(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NanoId> allActive() {

        return repository.edges().allActiveIds();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NanoId> all() {

        return repository.edges().allIds();
    }

    @Override
    @Transactional
    public Optional<Edge> expire(final NanoId id) {

        final var activeEdge = edgeOperations.findActive(id);
        if (activeEdge.isPresent()) {
            final var expired = edgeOperations.expire(id, Instant.now());
            repository.edges().expire(id, expired.expired().get());
            return Optional.of(expired);
        }
        return Optional.empty();
    }

    @Override
    @Transactional
    public boolean delete(final NanoId id) {

        return repository.edges().delete(id);
    }
}
