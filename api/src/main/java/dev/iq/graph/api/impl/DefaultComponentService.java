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
import dev.iq.graph.api.ComponentService;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.jgrapht.ComponentOperations;
import dev.iq.graph.persistence.GraphRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Default implementation of ComponentService using session-based transactions.
 */
public final class DefaultComponentService implements ComponentService {

    private final SessionFactory sessionFactory;

    private final GraphRepository repository;
    
    private final ComponentOperations componentOperations;

    public DefaultComponentService(final SessionFactory sessionFactory, final GraphRepository repository, final ComponentOperations componentOperations) {

        this.sessionFactory = sessionFactory;
        this.repository = repository;
        this.componentOperations = componentOperations;
    }

    @Override
    public Component add(final List<Element> elements, final Data data) {

        return SessionExecutor.execute(sessionFactory, () -> {
            final var component = componentOperations.add(elements, data, Instant.now());
            return repository.components().save(component);
        });
    }

    @Override
    public Component update(final NanoId id, final List<Element> elements, final Data data) {

        return SessionExecutor.execute(sessionFactory, () -> {
            final var component = componentOperations.update(id, elements, data, Instant.now());
            repository.components().save(component);
            return component;
        });
    }

    @Override
    public List<Component> findActiveContaining(final NanoId id) {

        try (final var session = sessionFactory.create()) {
            // Find all active components and check if they contain an element with this ID
            return componentOperations.allActive().stream()
                .filter(component -> component.elements().stream()
                    .anyMatch(element -> element.locator().id().equals(id) && element.expired().isEmpty()))
                .toList();
        }
    }

    @Override
    public List<Component> findContaining(final NanoId id, final Instant timestamp) {

        try (final var session = sessionFactory.create()) {
            // Find all components active at the timestamp and check if they contain the element
            final var allComponents = new ArrayList<Component>();
            
            // Get all component IDs from the in-memory operations
            for (final var component : componentOperations.allActive()) {
                allComponents.addAll(componentOperations.findAllVersions(component.locator().id()));
            }
            
            return allComponents.stream()
                .filter(component -> {
                    // Component must be active at the timestamp
                    if (component.created().isAfter(timestamp)) {
                        return false;
                    }
                    if (component.expired().isPresent() && !component.expired().get().isAfter(timestamp)) {
                        return false;
                    }
                    // Check if any element matches the ID
                    return component.elements().stream()
                        .anyMatch(element -> element.locator().id().equals(id));
                })
                .toList();
        }
    }

    @Override
    public Component find(final Locator locator) {

        try (final var session = sessionFactory.create()) {
            return repository.components().find(locator)
                .orElseThrow(() -> new IllegalArgumentException("Component not found: " + locator));
        }
    }

    @Override
    public Optional<Component> findActive(final NanoId id) {

        try (final var session = sessionFactory.create()) {
            return repository.components().findActive(id);
        }
    }

    @Override
    public Optional<Component> findAt(final NanoId id, final Instant timestamp) {

        try (final var session = sessionFactory.create()) {
            return repository.components().findAt(id, timestamp);
        }
    }

    @Override
    public List<Component> findAllVersions(final NanoId id) {

        try (final var session = sessionFactory.create()) {
            return repository.components().findAll(id);
        }
    }

    @Override
    public List<NanoId> allActive() {

        try (final var session = sessionFactory.create()) {
            // This would need proper implementation with repository support
            return all().stream()
                .filter(id -> findActive(id).isPresent())
                .collect(Collectors.toList());
        }
    }

    @Override
    public List<NanoId> all() {

        try (final var session = sessionFactory.create()) {
            // TODO: This requires repository enhancement to get all component IDs
            throw new UnsupportedOperationException("Not yet implemented - requires repository enhancement to get all component IDs");
        }
    }

    @Override
    public Optional<Component> expire(final NanoId id) {

        return SessionExecutor.execute(sessionFactory, () -> {
            final var activeComponent = componentOperations.findActive(id);
            if (activeComponent.isPresent()) {
                final var expired = componentOperations.expire(id, Instant.now());
                repository.components().expire(id, expired.expired().get());
                return Optional.of(expired);
            }
            return Optional.empty();
        });
    }

    @Override
    public boolean delete(final NanoId id) {

        return SessionExecutor.execute(sessionFactory, () ->
            repository.components().delete(id)
        );
    }
}