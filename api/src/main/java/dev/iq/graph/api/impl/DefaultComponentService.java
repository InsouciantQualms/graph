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
import dev.iq.graph.model.GraphSpace;
import dev.iq.graph.model.jgrapht.JGraphtGraphBuilder;
import dev.iq.graph.model.operations.ComponentOperations;
import dev.iq.graph.model.simple.SimpleType;
import dev.iq.graph.persistence.GraphRepository;
import dev.iq.graph.persistence.Session;
import dev.iq.graph.persistence.SessionFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of ComponentService that delegates to persistence layer and model operations.
 */
public final class DefaultComponentService implements ComponentService {

    private final GraphRepository graphRepository;
    private final SessionFactory sessionFactory;
    private final ComponentOperations componentOperations;

    public DefaultComponentService(
            final GraphRepository graphRepository,
            final SessionFactory sessionFactory,
            final ComponentOperations componentOperations) {
        this.graphRepository = graphRepository;
        this.sessionFactory = sessionFactory;
        this.componentOperations = componentOperations;
    }

    @Override
    public Component add(final List<Element> elements, final Data data) {
        try (final Session session = sessionFactory.create()) {
            final Component component = componentOperations.add(new SimpleType("COMPONENT"), data, Instant.now());

            // Now we need to update the elements to reference this component
            // This would require updating edges to include the component locator
            // For now, we'll just return the component

            session.commit();
            return component;
        } catch (Exception e) {
            throw new RuntimeException("Failed to add component", e);
        }
    }

    @Override
    public Component update(final NanoId id, final List<Element> elements, final Data data) {
        try (final Session session = sessionFactory.create()) {
            // Find the existing component
            final Optional<Component> existingComponent =
                    graphRepository.components().findActive(id);
            if (existingComponent.isEmpty()) {
                throw new IllegalArgumentException("Component not found: " + id);
            }

            final Component updatedComponent =
                    componentOperations.update(id, existingComponent.get().type(), data, Instant.now());

            // Now we need to update the elements to reference this component
            // This would require updating edges to include the component locator
            // For now, we'll just return the updated component

            session.commit();
            return updatedComponent;
        } catch (Exception e) {
            throw new RuntimeException("Failed to update component", e);
        }
    }

    @Override
    public List<Component> findActiveContaining(final NanoId elementId) {
        final GraphSpace graphSpace = buildGraphSpace();
        final List<Component> containingComponents = new ArrayList<>();

        // Check all active components
        graphRepository.components().allActiveIds().forEach(componentId -> {
            graphRepository.components().findActive(componentId).ifPresent(component -> {
                // Check if this component contains the element
                if (containsElement(component, elementId)) {
                    containingComponents.add(component);
                }
            });
        });

        return containingComponents;
    }

    @Override
    public List<Component> findContaining(final NanoId elementId, final Instant timestamp) {
        final List<Component> containingComponents = new ArrayList<>();

        // Check all components at the given timestamp
        graphRepository.components().allIds().forEach(componentId -> {
            graphRepository.components().findAt(componentId, timestamp).ifPresent(component -> {
                // Check if this component contains the element
                if (containsElement(component, elementId)) {
                    containingComponents.add(component);
                }
            });
        });

        return containingComponents;
    }

    @Override
    public Component find(final Locator locator) {
        final Component component = graphRepository.components().find(locator);
        if (component == null) {
            throw new IllegalArgumentException("Component not found: " + locator);
        }
        return component;
    }

    @Override
    public Optional<Component> findActive(final NanoId id) {
        return graphRepository.components().findActive(id);
    }

    @Override
    public Optional<Component> findAt(final NanoId id, final Instant timestamp) {
        return graphRepository.components().findAt(id, timestamp);
    }

    @Override
    public List<Component> findVersions(final NanoId id) {
        return graphRepository.components().findVersions(id);
    }

    @Override
    public List<NanoId> allActive() {
        return graphRepository.components().allActiveIds();
    }

    @Override
    public List<NanoId> all() {
        return graphRepository.components().allIds();
    }

    @Override
    public Optional<Component> expire(final NanoId id) {
        try (final Session session = sessionFactory.create()) {
            final Optional<Component> component = graphRepository.components().findActive(id);
            if (component.isPresent()) {
                final Component expiredComponent = componentOperations.expire(id, Instant.now());
                session.commit();
                return Optional.of(expiredComponent);
            }
            return Optional.empty();
        } catch (Exception e) {
            throw new RuntimeException("Failed to expire component", e);
        }
    }

    @Override
    public boolean delete(final NanoId id) {
        try (final Session session = sessionFactory.create()) {
            final boolean deleted = graphRepository.components().delete(id);
            session.commit();
            return deleted;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete component", e);
        }
    }

    private boolean containsElement(final Component component, final NanoId elementId) {
        // Components don't directly contain edges in the new architecture
        // Instead, edges reference components via their locators
        // We would need to check all edges in the graph to see if they reference this component
        // For now, return false as this logic needs to be implemented differently
        return false;
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
