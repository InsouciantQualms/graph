/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.tinkerpop;

import static org.apache.tinkerpop.gremlin.process.traversal.P.gt;
import static org.apache.tinkerpop.gremlin.process.traversal.P.lte;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Reference;
import dev.iq.graph.model.serde.PropertiesSerde;
import dev.iq.graph.model.serde.Serde;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.model.simple.SimpleType;
import dev.iq.graph.persistence.ExtendedVersionedRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

/**
 * Tinkerpop implementation of NodeRepository.
 */
@Repository("tinkerpopNodeRepository")
public final class TinkerpopNodeRepository implements ExtendedVersionedRepository<Node> {

    private final Graph graph;
    private final GraphTraversalSource traversal;
    private final Serde<Map<String, Object>> serde = new PropertiesSerde();

    public TinkerpopNodeRepository(final @NotNull Graph graph) {
        this.graph = graph;
        traversal = graph.traversal();
    }

    @Override
    public Node save(final @NotNull Node node) {
        final var vertex = graph.addVertex("node");
        vertex.property("id", node.locator().id().id());
        vertex.property("versionId", node.locator().version());
        vertex.property("type", node.type().code());
        vertex.property("created", node.created().toString());
        node.expired().ifPresent(expired -> vertex.property("expired", expired.toString()));

        final var properties = serde.serialize(node.data());
        properties.forEach(vertex::property);

        return node;
    }

    @Override
    public Optional<Node> findActive(final NanoId nodeId) {
        return traversal
                .V()
                .hasLabel("node")
                .has("id", nodeId.id())
                .not(__.has("expired"))
                .tryNext()
                .map(this::vertexToNode);
    }

    @Override
    public List<Node> findAll(final NanoId nodeId) {
        return traversal.V().hasLabel("node").has("id", nodeId.id()).order().by("versionId").toList().stream()
                .map(this::vertexToNode)
                .toList();
    }

    @Override
    public Optional<Node> find(final Locator locator) {
        final var vertex = traversal
                .V()
                .hasLabel("node")
                .has("id", locator.id().id())
                .has("versionId", locator.version())
                .tryNext();
        return vertex.map(this::vertexToNode);
    }

    @Override
    public Optional<Node> findAt(final NanoId nodeId, final Instant timestamp) {
        final var timestampStr = timestamp.toString();
        return traversal
                .V()
                .hasLabel("node")
                .has("id", nodeId.id())
                .where(__.values("created").is(lte(timestampStr)))
                .where(__.or(__.not(__.has("expired")), __.values("expired").is(gt(timestampStr))))
                .order()
                .by("versionId", Order.desc)
                .limit(1)
                .tryNext()
                .map(this::vertexToNode);
    }

    @Override
    public boolean delete(final NanoId nodeId) {
        final var count =
                traversal.V().hasLabel("node").has("id", nodeId.id()).count().next();
        if (count > 0) {
            traversal.V().hasLabel("node").has("id", nodeId.id()).drop().iterate();
            return true;
        }
        return false;
    }

    @Override
    public boolean expire(final NanoId elementId, final Instant expiredAt) {
        final var vertices = traversal
                .V()
                .hasLabel("node")
                .has("id", elementId.id())
                .not(__.has("expired"))
                .toList();
        if (!vertices.isEmpty()) {
            vertices.forEach(v -> v.property("expired", expiredAt.toString()));
            return true;
        }
        return false;
    }

    @Override
    public List<NanoId> allIds() {
        return traversal.V().hasLabel("node").values("id").dedup().toList().stream()
                .map(id -> new NanoId((String) id))
                .toList();
    }

    @Override
    public List<NanoId> allActiveIds() {
        return traversal.V().hasLabel("node").not(__.has("expired")).values("id").dedup().toList().stream()
                .map(id -> new NanoId((String) id))
                .toList();
    }

    private Node vertexToNode(final Vertex vertex) {
        final var id = new NanoId(vertex.value("id"));
        final int versionId = vertex.value("versionId");
        final var type = new SimpleType(vertex.value("type"));
        final var created = Instant.parse(vertex.value("created"));

        Optional<Instant> expired = Optional.empty();
        if (vertex.property("expired").isPresent()) {
            expired = Optional.of(Instant.parse(vertex.value("expired")));
        }

        final var properties = vertex.keys().stream()
                .filter(key -> !List.of("id", "versionId", "type", "created", "expired")
                        .contains(key))
                .collect(Collectors.toMap(Function.identity(), vertex::<Object>value));

        final var data = serde.deserialize(properties);
        final var locator = new Locator(id, versionId);

        // Find edges where this node is the source or target
        final var edgeRefs = new HashSet<Reference<Edge>>();

        // Find outgoing edges (where this node is the source)
        traversal
                .E()
                .hasLabel("edge")
                .has("sourceId", id.id())
                .has("sourceVersionId", versionId)
                .not(__.has("expired"))
                .toList()
                .forEach(edge -> {
                    final var edgeId = new NanoId(edge.value("id"));
                    final var edgeVersion = (int) edge.value("versionId");
                    final var edgeLocator = new Locator(edgeId, edgeVersion);
                    edgeRefs.add(new Reference.Unloaded<>(edgeLocator, Edge.class));
                });

        // Find incoming edges (where this node is the target)
        traversal
                .E()
                .hasLabel("edge")
                .has("targetId", id.id())
                .has("targetVersionId", versionId)
                .not(__.has("expired"))
                .toList()
                .forEach(edge -> {
                    final var edgeId = new NanoId(edge.value("id"));
                    final var edgeVersion = (int) edge.value("versionId");
                    final var edgeLocator = new Locator(edgeId, edgeVersion);
                    edgeRefs.add(new Reference.Unloaded<>(edgeLocator, Edge.class));
                });

        // Find components containing this node
        final var componentRefs = new HashSet<Reference<Component>>();
        traversal
                .E()
                .hasLabel("component-element")
                .has("elementId", id.id())
                .has("elementVersionId", versionId)
                .has("elementType", "SimpleNode")
                .toList()
                .forEach(componentEdge -> {
                    final var componentId = new NanoId(componentEdge.value("componentId"));
                    final var componentVersion = (int) componentEdge.value("componentVersionId");
                    final var componentLocator = new Locator(componentId, componentVersion);
                    componentRefs.add(new Reference.Unloaded<>(componentLocator, Component.class));
                });

        return new SimpleNode(locator, type, new ArrayList<>(edgeRefs), data, created, expired, componentRefs);
    }
}
