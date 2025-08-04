/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.tinkerpop;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Node;
import dev.iq.graph.persistence.ExtendedVersionedRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Tinkerpop Node Repository with referential integrity support.
 * Wraps the base TinkerpopNodeRepository and adds referential integrity operations.
 */
public final class TinkerpopNodeRepositoryWithIntegrity implements ExtendedVersionedRepository<Node> {

    private final TinkerpopNodeRepository baseRepository;
    private final TinkerpopReferentialIntegrityManager integrityManager;

    public TinkerpopNodeRepositoryWithIntegrity(
            final TinkerpopNodeRepository baseRepository, final TinkerpopReferentialIntegrityManager integrityManager) {
        this.baseRepository = baseRepository;
        this.integrityManager = integrityManager;
    }

    @Override
    public Node save(final Node node) {
        // If this is an update (version > 1), handle referential integrity
        if (node.locator().version() > 1) {
            // Find the previous version
            final var previousLocator =
                    new Locator(node.locator().id(), node.locator().version() - 1);
            try {
                final var previousNode = baseRepository.find(previousLocator);
                if (previousNode.expired().isEmpty()) {
                    // This is an update, not just adding a new version
                    // The previous version should be expired before saving the new one
                    baseRepository.expire(previousNode.locator().id(), node.created());
                    final var savedNode = baseRepository.save(node);

                    // Handle referential integrity for connected edges
                    integrityManager.handleNodeUpdate(previousNode, savedNode, node.created());

                    return savedNode;
                }
            } catch (IllegalArgumentException e) {
                // Previous version doesn't exist, this is a new node with version > 1
                // This shouldn't normally happen, but we'll allow it
            }
        }

        return baseRepository.save(node);
    }

    @Override
    public Optional<Node> findActive(final NanoId nodeId) {
        return baseRepository.findActive(nodeId);
    }

    @Override
    public List<Node> findVersions(final NanoId nodeId) {
        return baseRepository.findVersions(nodeId);
    }

    @Override
    public Node find(final Locator locator) {
        return baseRepository.find(locator);
    }

    @Override
    public Optional<Node> findAt(final NanoId nodeId, final Instant timestamp) {
        return baseRepository.findAt(nodeId, timestamp);
    }

    @Override
    public boolean delete(final NanoId nodeId) {
        return baseRepository.delete(nodeId);
    }

    @Override
    public boolean expire(final NanoId nodeId, final Instant expiredAt) {
        final var result = baseRepository.expire(nodeId, expiredAt);

        if (result) {
            // Handle referential integrity for connected edges
            integrityManager.handleNodeExpiry(nodeId, expiredAt);
        }

        return result;
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
    public List<Node> findAll(final NanoId nodeId) {
        return baseRepository.findAll(nodeId);
    }

    /**
     * Updates a node with referential integrity.
     * This is a convenience method that handles expiring the old version,
     * saving the new version, and updating all connected edges.
     */
    public Node update(final Node oldNode, final Node newNode, final Instant timestamp) {
        // Expire the old node
        baseRepository.expire(oldNode.locator().id(), timestamp);

        // Save the new node
        final var savedNode = baseRepository.save(newNode);

        // Handle referential integrity
        integrityManager.handleNodeUpdate(oldNode, savedNode, timestamp);

        return savedNode;
    }
}
