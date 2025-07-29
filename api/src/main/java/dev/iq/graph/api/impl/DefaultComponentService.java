/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api.impl;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.api.ComponentService;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.jgrapht.ComponentOperations;
import dev.iq.graph.persistence.GraphRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of ComponentService using session-based transactions.
 */
@Service
public final class DefaultComponentService implements ComponentService {

    private final GraphRepository repository;

    private final ComponentOperations componentOperations;

    public DefaultComponentService(final GraphRepository repository, final ComponentOperations componentOperations) {

        this.repository = repository;
        this.componentOperations = componentOperations;
    }

    @Override
    @Transactional
    public Component add(final List<Element> elements, final Data data) {

        final var component = componentOperations.add(elements, data, Instant.now());
        return repository.components().save(component);
    }

    @Override
    @Transactional
    public Component update(final NanoId id, final List<Element> elements, final Data data) {

        final var component = componentOperations.update(id, elements, data, Instant.now());
        repository.components().save(component);
        return component;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Component> findActiveContaining(final NanoId id) {

        // Get all active component IDs from repository and filter
        final var activeComponentIds = repository.components().allActiveIds();
        final var activeComponents = new java.util.ArrayList<Component>();

        for (final var componentId : activeComponentIds) {
            componentOperations
                    .findActive(componentId)
                    .filter(component -> component.elements().stream()
                            .anyMatch(elementRef -> elementRef.locator().id().equals(id)))
                    .ifPresent(activeComponents::add);
        }

        return activeComponents;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Component> findContaining(final NanoId id, final Instant timestamp) {

        // Get all component IDs and check all versions
        final var allComponentIds = repository.components().allIds();
        final var matchingComponents = new java.util.ArrayList<Component>();

        for (final var componentId : allComponentIds) {
            final var versions = componentOperations.findVersions(componentId);
            for (final var component : versions) {
                if (!component.created().isAfter(timestamp)
                        && (component.expired().isEmpty()
                                || component.expired().get().isAfter(timestamp))
                        && component.elements().stream()
                                .anyMatch(
                                        elementRef -> elementRef.locator().id().equals(id))) {
                    matchingComponents.add(component);
                }
            }
        }

        return matchingComponents;
    }

    @Override
    @Transactional(readOnly = true)
    public Component find(final Locator locator) {

        return repository
                .components()
                .find(locator)
                .orElseThrow(() -> new IllegalArgumentException("Component not found: " + locator));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Component> findActive(final NanoId id) {

        return repository.components().findActive(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Component> findAt(final NanoId id, final Instant timestamp) {

        return repository.components().findAt(id, timestamp);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Component> findAllVersions(final NanoId id) {

        return repository.components().findAll(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NanoId> allActive() {

        return repository.components().allActiveIds();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NanoId> all() {

        return repository.components().allIds();
    }

    @Override
    @Transactional
    public Optional<Component> expire(final NanoId id) {

        final var activeComponent = componentOperations.findActive(id);
        if (activeComponent.isPresent()) {
            final var expired = componentOperations.expire(id, Instant.now());
            if (expired.expired().isEmpty()) {
                throw new IllegalStateException("Expired component is missing an expiration timestamp");
            }
            repository.components().expire(id, expired.expired().get());
            return Optional.of(expired);
        }
        return Optional.empty();
    }

    @Override
    @Transactional
    public boolean delete(final NanoId id) {

        return repository.components().delete(id);
    }
}
