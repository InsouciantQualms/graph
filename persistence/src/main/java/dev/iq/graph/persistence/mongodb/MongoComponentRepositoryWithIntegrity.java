/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb;

import com.mongodb.client.MongoDatabase;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.common.version.Uid;
import dev.iq.graph.model.Component;
import dev.iq.graph.persistence.ExtendedVersionedRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB Component Repository with referential integrity support.
 * Wraps the base MongoComponentRepository and adds referential integrity operations.
 */
public final class MongoComponentRepositoryWithIntegrity implements ExtendedVersionedRepository<Component> {

    private final MongoComponentRepository baseRepository;
    private final MongoReferentialIntegrityManager integrityManager;

    public MongoComponentRepositoryWithIntegrity(
            final MongoDatabase database, final MongoReferentialIntegrityManager integrityManager) {
        this.baseRepository = new MongoComponentRepository(database);
        this.integrityManager = integrityManager;
    }

    @Override
    public Component save(final Component component) {
        // If this is an update (version > 1), handle referential integrity
        if (component.locator().version() > 1) {
            // Find the previous version
            final var previousLocator =
                    new Locator(component.locator().id(), component.locator().version() - 1);
            try {
                final var previousComponent = baseRepository.find(previousLocator);
                if (previousComponent.expired().isEmpty()) {
                    // This is an update, not just adding a new version
                    // The previous version should be expired before saving the new one
                    baseRepository.expire(previousComponent.locator().id(), component.created());
                    final var savedComponent = baseRepository.save(component);

                    // Handle referential integrity for elements referencing this component
                    integrityManager.handleComponentUpdate(previousComponent, savedComponent, component.created());

                    return savedComponent;
                }
            } catch (IllegalArgumentException e) {
                // Previous version doesn't exist, this is a new component with version > 1
                // This shouldn't normally happen, but we'll allow it
            }
        }

        return baseRepository.save(component);
    }

    @Override
    public Optional<Component> findActive(final Uid componentId) {
        return baseRepository.findActive(componentId);
    }

    @Override
    public List<Component> findVersions(final Uid componentId) {
        return baseRepository.findVersions(componentId);
    }

    @Override
    public Component find(final Locator locator) {
        return baseRepository.find(locator);
    }

    @Override
    public Optional<Component> findAt(final Uid componentId, final Instant timestamp) {
        return baseRepository.findAt(componentId, timestamp);
    }

    @Override
    public boolean delete(final Uid componentId) {
        return baseRepository.delete(componentId);
    }

    @Override
    public boolean expire(final Uid componentId, final Instant expiredAt) {
        // Components don't cascade expiry to elements - elements continue to reference expired components
        return baseRepository.expire(componentId, expiredAt);
    }

    @Override
    public List<NanoId> allIds() {
        return baseRepository.allIds();
    }

    @Override
    public List<NanoId> allActiveIds() {
        return baseRepository.allActiveIds();
    }

    @Override
    public List<Component> findAll(final Uid componentId) {
        return baseRepository.findAll(componentId);
    }

    /**
     * Updates a component with referential integrity.
     * This is a convenience method that handles expiring the old version,
     * saving the new version, and updating all elements that reference this component.
     */
    public Component update(final Component oldComponent, final Component newComponent, final Instant timestamp) {
        // Expire the old component
        baseRepository.expire(oldComponent.locator().id(), timestamp);

        // Save the new component
        final var savedComponent = baseRepository.save(newComponent);

        // Handle referential integrity
        integrityManager.handleComponentUpdate(oldComponent, savedComponent, timestamp);

        return savedComponent;
    }
}
