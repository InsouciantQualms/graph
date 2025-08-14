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
import dev.iq.common.version.Uid;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.model.simple.SimpleType;
import dev.iq.graph.persistence.ExtendedVersionedRepository;
import dev.iq.graph.persistence.serde.PropertiesSerde;
import dev.iq.graph.persistence.serde.Serde;
import java.time.Instant;
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
        vertex.property("id", node.locator().id().code());
        vertex.property("versionId", node.locator().version());
        vertex.property("type", node.type().code());
        vertex.property("created", node.created().toString());
        node.expired().ifPresent(expired -> vertex.property("expired", expired.toString()));

        final var properties = serde.serialize(node.data());
        properties.forEach(vertex::property);

        // Nodes no longer have components in the new model

        return node;
    }

    @Override
    public Optional<Node> findActive(final Uid nodeId) {
        return traversal
                .V()
                .hasLabel("node")
                .has("id", nodeId.code())
                .not(__.has("expired"))
                .tryNext()
                .map(this::vertexToNode);
    }

    @Override
    public List<Node> findAll(final Uid nodeId) {
        return traversal.V().hasLabel("node").has("id", nodeId.code()).order().by("versionId").toList().stream()
                .map(this::vertexToNode)
                .toList();
    }

    @Override
    public List<Node> findVersions(final Uid nodeId) {
        return findAll(nodeId);
    }

    @Override
    public Node find(final Locator locator) {
        final var vertex = traversal
                .V()
                .hasLabel("node")
                .has("id", locator.id().code())
                .has("versionId", locator.version())
                .tryNext();
        return vertex.map(this::vertexToNode)
                .orElseThrow(() -> new IllegalArgumentException("Node not found for locator: " + locator));
    }

    @Override
    public Optional<Node> findAt(final Uid nodeId, final Instant timestamp) {
        final var timestampStr = timestamp.toString();
        return traversal
                .V()
                .hasLabel("node")
                .has("id", nodeId.code())
                .where(__.values("created").is(lte(timestampStr)))
                .where(__.or(__.not(__.has("expired")), __.values("expired").is(gt(timestampStr))))
                .order()
                .by("versionId", Order.desc)
                .limit(1)
                .tryNext()
                .map(this::vertexToNode);
    }

    @Override
    public boolean delete(final Uid nodeId) {
        final var count =
                traversal.V().hasLabel("node").has("id", nodeId.code()).count().next();
        if (count > 0) {
            traversal.V().hasLabel("node").has("id", nodeId.code()).drop().iterate();
            return true;
        }
        return false;
    }

    @Override
    public boolean expire(final Uid elementId, final Instant expiredAt) {
        final var vertices = traversal
                .V()
                .hasLabel("node")
                .has("id", elementId.code())
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
                .map(id -> NanoId.from((String) id))
                .toList();
    }

    @Override
    public List<NanoId> allActiveIds() {
        return traversal.V().hasLabel("node").not(__.has("expired")).values("id").dedup().toList().stream()
                .map(id -> NanoId.from((String) id))
                .toList();
    }

    private Node vertexToNode(final Vertex vertex) {
        final var id = NanoId.from(vertex.value("id"));
        final int versionId = vertex.value("versionId");
        final var type = new SimpleType(vertex.value("type"));
        final var created = Instant.parse(vertex.value("created"));

        Optional<Instant> expired = Optional.empty();
        if (vertex.property("expired").isPresent()) {
            expired = Optional.of(Instant.parse(vertex.value("expired")));
        }

        final var properties = vertex.keys().stream()
                .filter(key -> !List.of("id", "versionId", "type", "created", "expired", "components")
                        .contains(key))
                .collect(Collectors.toMap(Function.identity(), vertex::<Object>value));

        final var data = serde.deserialize(properties);
        final var locator = new Locator(id, versionId);
        return new SimpleNode(locator, type, data, created, expired);
    }

    // Nodes no longer have components in the new model - methods removed
}
