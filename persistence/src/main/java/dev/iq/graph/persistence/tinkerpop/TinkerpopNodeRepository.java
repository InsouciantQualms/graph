package dev.iq.graph.persistence.tinkerpop;

import dev.iq.common.persist.VersionedRepository;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.serde.PropertiesSerde;
import dev.iq.graph.model.serde.Serde;
import dev.iq.graph.model.simple.SimpleNode;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.tinkerpop.gremlin.process.traversal.P.gt;
import static org.apache.tinkerpop.gremlin.process.traversal.P.lte;

/**
 * Tinkerpop implementation of NodeRepository.
 */
public final class TinkerpopNodeRepository implements VersionedRepository<Node> {

    private final Graph graph;
    private final GraphTraversalSource g;
    private final Serde<Map<String, Object>> serde = new PropertiesSerde();

    public TinkerpopNodeRepository(final @NotNull Graph graph) {
        this.graph = graph;
        g = graph.traversal();
    }

    @Override
    public Node save(final @NotNull Node node) {
        final var vertex = graph.addVertex("node");
        vertex.property("id", node.locator().id().id());
        vertex.property("versionId", node.locator().version());
        vertex.property("created", node.created().toString());
        node.expired().ifPresent(expired -> vertex.property("expired", expired.toString()));

        final var properties = serde.serialize(node.data());
        properties.forEach(vertex::property);

        return node;
    }

    @Override
    public Optional<Node> findActive(final NanoId nodeId) {
        return g.V().hasLabel("node")
            .has("id", nodeId.id())
            .not(__.has("expired"))
            .tryNext()
            .map(this::vertexToNode);
    }

    @Override
    public List<Node> findAll(final NanoId nodeId) {
        return g.V().hasLabel("node")
            .has("id", nodeId.id())
            .order().by("versionId")
            .toList().stream()
            .map(this::vertexToNode)
            .toList();
    }

    @Override
    public Optional<Node> find(final Locator locator) {
        final var vertex = g.V()
            .hasLabel("node")
            .has("id", locator.id().id())
            .has("versionId", locator.version())
            .tryNext();
        return vertex.map(this::vertexToNode);
    }

    @Override
    public Optional<Node> findAt(final NanoId nodeId, final Instant timestamp) {
        final var timestampStr = timestamp.toString();
        return g.V().hasLabel("node")
            .has("id", nodeId.id())
            .where(__.values("created").is(lte(timestampStr)))
            .where(__.or(__.not(__.has("expired")),
                __.values("expired").is(gt(timestampStr))
            ))
            .order().by("versionId", Order.desc)
            .limit(1)
            .tryNext()
            .map(this::vertexToNode);
    }

    @Override
    public boolean delete(final NanoId nodeId) {
        final var count = g.V().hasLabel("node").has("id", nodeId.id()).count().next();
        if (count > 0) {
            g.V().hasLabel("node").has("id", nodeId.id()).drop().iterate();
            return true;
        }
        return false;
    }

    @Override
    public boolean expire(final NanoId elementId, final Instant expiredAt) {
        final var vertices = g.V().hasLabel("node")
            .has("id", elementId.id())
            .not(__.has("expired"))
            .toList();
        if (!vertices.isEmpty()) {
            vertices.forEach(v -> v.property("expired", expiredAt.toString()));
            return true;
        }
        return false;
    }

    private Node vertexToNode(final Vertex vertex) {
        final var id = new NanoId(vertex.value("id"));
        final int versionId = vertex.value("versionId");
        final var created = Instant.parse(vertex.value("created"));

        Optional<Instant> expired = Optional.empty();
        if (vertex.property("expired").isPresent()) {
            expired = Optional.of(Instant.parse(vertex.value("expired")));
        }

        final var properties = vertex.keys().stream()
            .filter(key -> !List.of("id", "versionId", "created", "expired").contains(key))
            .collect(Collectors.toMap(
                Function.identity(),
                vertex::<Object>value
            ));

        final var data = serde.deserialize(properties);
        final var locator = new Locator(id, versionId);

        return new SimpleNode(locator, List.of(), data, created, expired);
    }
}