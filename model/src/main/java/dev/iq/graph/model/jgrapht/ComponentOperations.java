/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Operations;
import dev.iq.graph.model.Reference;
import dev.iq.graph.model.simple.SimpleComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.jgrapht.Graph;

/**
 * Operations for managing components in a JGraphT graph.
 *
 * This implementation uses a hybrid approach:
 * - Component metadata (versions, data, timestamps) is stored in componentVersions map
 * - Component membership is tracked separately in elementToComponents map
 * - The actual graph elements remain unchanged to maintain graph integrity
 *
 * Note: Due to the immutable nature of SimpleNode/SimpleEdge records and their
 * inclusion of components in equals/hashCode, we cannot modify Element#components()
 * without breaking graph lookups. Instead, we track membership externally.
 */
public final class ComponentOperations implements Operations<Component> {

    private final Graph<Reference<Node>, Reference<Edge>> graph;
    private final Map<NanoId, List<Component>> componentVersions;
    private final Map<Element, Set<NanoId>> elementToComponents;

    public ComponentOperations(final Graph<Reference<Node>, Reference<Edge>> graph) {

        this.graph = graph;
        componentVersions = new HashMap<>();
        elementToComponents = new HashMap<>();
    }

    /**
     * Adds a new component with the specified elements and data.
     */
    public Component add(final List<Element> elements, final Data data, final Instant timestamp) {

        OperationsHelper.validateComponentElements(elements, graph);
        final var locator = Locator.generate();

        // Track component membership externally
        elements.forEach(element -> elementToComponents
                .computeIfAbsent(element, k -> new HashSet<>())
                .add(locator.id()));

        // Create component with the original elements as loaded references
        final List<Reference<Element>> elementRefs = elements.stream()
                .map(e -> (Reference<Element>) new Reference.Loaded<>(e))
                .toList();
        final var component = new SimpleComponent(locator, elementRefs, data, timestamp, Optional.empty());

        // Store component version
        componentVersions.computeIfAbsent(locator.id(), k -> new ArrayList<>()).add(component);

        return component;
    }

    /**
     * Updates an existing component with new elements and data.
     */
    public Component update(final NanoId id, final List<Element> elements, final Data data, final Instant timestamp) {

        OperationsHelper.validateComponentElements(elements, graph);
        final var existingComponent = OperationsHelper.validateForExpiry(findActive(id), id, "Component");

        // Remove component reference from old elements
        existingComponent.elements().forEach(element -> {
            final var componentSet = elementToComponents.get(element);
            if (componentSet != null) {
                componentSet.remove(id);
                if (componentSet.isEmpty()) {
                    elementToComponents.remove(element);
                }
            }
        });

        final var expired = expire(id, timestamp);
        final var incremented = expired.locator().increment();

        // Add component reference to new elements
        elements.forEach(element -> elementToComponents
                .computeIfAbsent(element, k -> new HashSet<>())
                .add(id));

        // Create component with the original elements as loaded references
        final List<Reference<Element>> newElementRefs = elements.stream()
                .map(e -> (Reference<Element>) new Reference.Loaded<>(e))
                .toList();
        final var newComponent = new SimpleComponent(incremented, newElementRefs, data, timestamp, Optional.empty());

        // Store new version
        componentVersions.get(id).add(newComponent);

        return newComponent;
    }

    @Override
    public Optional<Component> findActive(final NanoId id) {

        final var versions = componentVersions.get(id);
        if (versions == null) {
            return Optional.empty();
        }

        return versions.stream().filter(c -> c.expired().isEmpty()).max(Comparator.comparingInt(c -> c.locator()
                .version()));
    }

    @Override
    public Optional<Component> findAt(final NanoId id, final Instant timestamp) {

        // Find the component version active at timestamp
        final var versions = componentVersions.get(id);
        if (versions == null) {
            return Optional.empty();
        }

        return versions.stream()
                .filter(c -> !c.created().isAfter(timestamp))
                .filter(c -> c.expired().isEmpty() || c.expired().get().isAfter(timestamp))
                .max(Comparator.comparingInt(c -> c.locator().version()));
    }

    @Override
    public List<Component> findVersions(final NanoId id) {

        final var versions = componentVersions.get(id);
        return (versions != null) ? new ArrayList<>(versions) : List.of();
    }

    @Override
    public Component find(final Locator locator) {
        return componentVersions.values().stream()
                .flatMap(List::stream)
                .filter(component -> component.locator().equals(locator))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Component not found: " + locator));
    }

    @Override
    public Component expire(final NanoId id, final Instant timestamp) {

        final var component = OperationsHelper.validateForExpiry(findActive(id), id, "Component");

        // Create expired version
        final var expiredComponent = new SimpleComponent(
                component.locator(),
                component.elements(),
                component.data(),
                component.created(),
                Optional.of(timestamp));

        // Update stored version
        final var versions = componentVersions.get(id);
        if (versions != null) {
            // Replace the active version with expired version
            versions.removeIf(c -> c.locator().equals(component.locator()));
            versions.add(expiredComponent);
        }

        return expiredComponent;
    }

    /**
     * Finds all components that contain the specified element.
     */
    public List<Component> findComponentsContaining(final Element element) {

        final var componentIds = elementToComponents.get(element);
        if ((componentIds == null) || componentIds.isEmpty()) {
            return List.of();
        }

        return componentIds.stream()
                .map(this::findActive)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
