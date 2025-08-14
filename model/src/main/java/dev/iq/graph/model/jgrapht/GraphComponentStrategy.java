/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Locator;
import dev.iq.common.version.Uid;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.operations.ComponentStrategy;
import dev.iq.graph.model.simple.SimpleComponent;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jgrapht.Graph;

/**
 * Component storage strategy that stores components directly on the graph as metadata nodes.
 * This approach is suitable for graph databases like TinkerPop.
 */
public final class GraphComponentStrategy implements ComponentStrategy {

    private static final String COMPONENT_MANIFEST_TYPE = "ComponentManifest";
    private static final String COMPONENT_ID_KEY = "componentId";
    private static final String COMPONENT_VERSION_KEY = "componentVersion";
    private static final String COMPONENT_TYPE_KEY = "componentType";
    private static final String COMPONENT_DATA_KEY = "componentData";
    private static final String COMPONENT_CREATED_KEY = "componentCreated";
    private static final String COMPONENT_EXPIRED_KEY = "componentExpired";

    private final Graph<Node, Edge> graph;
    private final Map<Uid, List<Node>> componentManifestNodes;

    public GraphComponentStrategy(final Graph<Node, Edge> graph) {
        this.graph = graph;
        this.componentManifestNodes = new HashMap<>();
    }

    @Override
    public void store(final Component component) {
        final var manifestNodes =
                componentManifestNodes.computeIfAbsent(component.locator().id(), k -> new ArrayList<>());

        // Remove existing manifest node with same locator if it exists (for expiry updates)
        manifestNodes.removeIf(node -> {
            if (node.locator().equals(component.locator())) {
                graph.removeVertex(node);
                return true;
            }
            return false;
        });

        // Create and add new manifest node
        final Node manifestNode = createManifestNode(component);
        graph.addVertex(manifestNode);
        manifestNodes.add(manifestNode);
    }

    @Override
    public Optional<Component> findActive(final Uid id) {
        final var manifestNodes = componentManifestNodes.get(id);
        if (manifestNodes == null) {
            return Optional.empty();
        }

        return manifestNodes.stream()
                .map(this::componentFromManifestNode)
                .filter(c -> c.expired().isEmpty())
                .max(Comparator.comparingInt(c -> c.locator().version()));
    }

    @Override
    public List<Component> findVersions(final Uid id) {
        final var manifestNodes = componentManifestNodes.get(id);
        if (manifestNodes == null) {
            return List.of();
        }

        return manifestNodes.stream().map(this::componentFromManifestNode).toList();
    }

    @Override
    public List<Component> allActive() {
        return componentManifestNodes.values().stream()
                .flatMap(List::stream)
                .map(this::componentFromManifestNode)
                .filter(c -> c.expired().isEmpty())
                .toList();
    }

    @Override
    public void updateReferences(final Locator oldLocator, final Locator newLocator) {
        // In graph storage, component references are maintained through edges
        // This method coordinates with the graph structure
    }

    @Override
    public Set<Edge> findEdgesReferencingComponent(final Locator componentLocator) {
        final Set<Edge> referencingEdges = new HashSet<>();
        for (final Edge edge : graph.edgeSet()) {
            if (edge.expired().isEmpty() && edge.components().contains(componentLocator)) {
                referencingEdges.add(edge);
            }
        }
        return referencingEdges;
    }

    @Override
    public void clear() {
        // Remove all manifest nodes from the graph
        final List<Node> nodesToRemove = new ArrayList<>();
        componentManifestNodes.values().stream().flatMap(List::stream).forEach(nodesToRemove::add);

        nodesToRemove.forEach(graph::removeVertex);
        componentManifestNodes.clear();
    }

    private Node createManifestNode(final Component component) {
        // Create a special node that represents the component manifest
        final Map<String, Object> manifestData = new HashMap<>();
        manifestData.put(COMPONENT_ID_KEY, component.locator().id().toString());
        manifestData.put(
                COMPONENT_VERSION_KEY, String.valueOf(component.locator().version()));
        // Store the actual type and data objects, not their string representations
        manifestData.put(COMPONENT_TYPE_KEY, component.type());
        manifestData.put(COMPONENT_DATA_KEY, component.data());
        manifestData.put(COMPONENT_CREATED_KEY, component.created().toString());
        component.expired().ifPresent(exp -> manifestData.put(COMPONENT_EXPIRED_KEY, exp.toString()));

        final Type manifestType = new SimpleType(COMPONENT_MANIFEST_TYPE);
        final Data manifestDataObj = new SimpleData(Map.class, manifestData);

        return new SimpleNode(
                component.locator(), manifestType, manifestDataObj, component.created(), component.expired());
    }

    private Component componentFromManifestNode(final Node manifestNode) {
        // Extract component data from the manifest node
        final Map<String, Object> manifestData = extractManifestData(manifestNode.data());

        final Type type = (Type) manifestData.get(COMPONENT_TYPE_KEY);
        final Data data = (Data) manifestData.get(COMPONENT_DATA_KEY);
        final String createdStr = (String) manifestData.get(COMPONENT_CREATED_KEY);
        final String expiredStr = (String) manifestData.get(COMPONENT_EXPIRED_KEY);

        final Instant created = Instant.parse(createdStr);
        final Optional<Instant> expired =
                expiredStr != null ? Optional.of(Instant.parse(expiredStr)) : Optional.empty();

        return new SimpleComponent(manifestNode.locator(), type, data, created, expired);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractManifestData(final Data data) {
        // Extract the map data from the Data object
        // This assumes SimpleData stores a Map internally
        if (data instanceof SimpleData simpleData) {
            final Object value = simpleData.value();
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
        }
        return new HashMap<>();
    }
}
