/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.common.version.Versions;
import dev.iq.graph.model.*;
import dev.iq.graph.model.simple.SimpleComponent;
import dev.iq.graph.model.simple.SimpleData;
import org.jgrapht.Graph;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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

    private final Graph<Node, Edge> graph;
    private final Map<NanoId, List<Component>> componentVersions;
    private final Map<Element, Set<NanoId>> elementToComponents;

    public ComponentOperations(final Graph<Node, Edge> graph) {

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
        for (final var element : elements) {
            elementToComponents.computeIfAbsent(element, k -> new HashSet<>()).add(locator.id());
        }

        // Create component with the original elements
        final var component = new SimpleComponent(locator, new ArrayList<>(elements), data, timestamp, Optional.empty());

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
        for (final var element : existingComponent.elements()) {
            final var componentSet = elementToComponents.get(element);
            if (componentSet != null) {
                componentSet.remove(id);
                if (componentSet.isEmpty()) {
                    elementToComponents.remove(element);
                }
            }
        }

        final var expired = expire(id, timestamp);
        final var incremented = expired.locator().increment();

        // Add component reference to new elements
        for (final var element : elements) {
            elementToComponents.computeIfAbsent(element, k -> new HashSet<>()).add(id);
        }

        // Create component with the original elements
        final var newComponent = new SimpleComponent(incremented, new ArrayList<>(elements), data, timestamp, Optional.empty());

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

        return versions.stream()
            .filter(c -> c.expired().isEmpty())
            .max(Comparator.comparingInt(c -> c.locator().version()));
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
    public List<Component> findAllVersions(final NanoId id) {

        final var versions = componentVersions.get(id);
        return (versions != null) ? new ArrayList<>(versions) : List.of();
    }

    @Override
    public List<Component> allActive() {

        return componentVersions.values().stream()
            .flatMap(List::stream)
            .filter(c -> c.expired().isEmpty())
            .toList();
    }

    @Override
    public Component expire(final NanoId id, final Instant timestamp) {

        final var component = OperationsHelper.validateForExpiry(findActive(id), id, "Component");

        // Create expired version
        final var expiredComponent = new SimpleComponent(
            component.locator(), component.elements(), component.data(),
            component.created(), Optional.of(timestamp)
        );

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
            .collect(Collectors.toList());
    }

    /**
     * Helper method to find the latest active component.
     */
    private Optional<Component> findLatestActiveComponent(final NanoId id, final List<Element> elements) {

        // Since we store components on the graph itself via element references,
        // we need a better way to track component metadata (version, data, timestamps)
        // For now, this is a limitation of the current design

        // Check if all elements in the component are active
        final var allActive = elements.stream().allMatch(e -> e.expired().isEmpty());
        if (!allActive) {
            return Optional.empty();
        }

        if (elements.isEmpty()) {
            return Optional.empty();
        }

        // Create a component representation
        final var locator = new Locator(id, 1); // Version tracking limitation
        final var data = new SimpleData(Component.class, "Component");
        final var created = elements.stream()
            .map(Element::created)
            .min(Instant::compareTo)
            .orElse(Instant.now());

        return Optional.of(new SimpleComponent(locator, new ArrayList<>(elements), data, created, Optional.empty()));
    }

    /**
     * Helper method to find component at specific timestamp.
     */
    private Optional<Component> findComponentAtTimestamp(final NanoId id, final Instant timestamp, final List<Element> elements) {

        // Similar to findLatestActiveComponent but for a specific timestamp
        if (elements.isEmpty()) {
            return Optional.empty();
        }

        final var locator = new Locator(id, 1); // Version tracking needed
        final var data = new SimpleData(Component.class, "Component at " + timestamp);
        return Optional.of(new SimpleComponent(locator, new ArrayList<>(elements), data, timestamp, Optional.empty()));
    }

    /**
     * Helper method to reconstruct all component versions.
     */
    private List<Component> reconstructAllComponentVersions(final NanoId id, final List<Element> elements) {

        // This would need proper version tracking
        // For now, return a single version
        final var component = findLatestActiveComponent(id, elements);
        return component.map(List::of).orElse(List.of());
    }

    /**
     * Validates that the elements form a valid component according to the rules.
     */
}
