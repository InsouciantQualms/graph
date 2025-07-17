/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.tinkerpop;

import static org.apache.tinkerpop.gremlin.process.traversal.P.gt;
import static org.apache.tinkerpop.gremlin.process.traversal.P.lte;

import dev.iq.common.persist.VersionedRepository;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.serde.PropertiesSerde;
import dev.iq.graph.model.serde.Serde;
import dev.iq.graph.model.simple.SimpleEdge;
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

/**
 * Tinkerpop implementation of EdgeRepository.
 */
public final class TinkerpopEdgeRepository implements VersionedRepository<Edge> {

    private final GraphTraversalSource g;
    private final TinkerpopNodeRepository nodeRepository;
    private final Serde<Map<String, Object>> serde = new PropertiesSerde();

    public TinkerpopEdgeRepository(final @NotNull Graph graph, final TinkerpopNodeRepository nodeRepository) {
        g = graph.traversal();
        this.nodeRepository = nodeRepository;
    }

    @Override
    public Edge save(final @NotNull Edge edge) {
        final var sourceVertex = findOrCreateVertexForNode(edge.source());
        final var targetVertex = findOrCreateVertexForNode(edge.target());

        final var tinkerpopEdge = sourceVertex.addEdge("edge", targetVertex);
        tinkerpopEdge.property("id", edge.locator().id().id());
        tinkerpopEdge.property("versionId", edge.locator().version());
        tinkerpopEdge.property("sourceId", edge.source().locator().id().id());
        tinkerpopEdge.property("sourceVersionId", edge.source().locator().version());
        tinkerpopEdge.property("targetId", edge.target().locator().id().id());
        tinkerpopEdge.property("targetVersionId", edge.target().locator().version());
        tinkerpopEdge.property("created", edge.created().toString());
        edge.expired().ifPresent(expired -> tinkerpopEdge.property("expired", expired.toString()));

        final var properties = serde.serialize(edge.data());
        properties.forEach(tinkerpopEdge::property);

        return edge;
    }

    @Override
    public Optional<Edge> findActive(final NanoId edgeId) {
        final var edgeOpt = g.E().has("id", edgeId.id()).not(__.has("expired")).tryNext();
        return edgeOpt.map(this::edgeToEdge);
    }

    @Override
    public List<Edge> findAll(final NanoId edgeId) {
        return g.E().has("id", edgeId.id()).order().by("versionId").toList().stream()
                .map(this::edgeToEdge)
                .toList();
    }

    @Override
    public Optional<Edge> find(final Locator locator) {
        final var edgeOpt = g.E().has("id", locator.id().id())
                .has("versionId", locator.version())
                .tryNext();
        return edgeOpt.map(this::edgeToEdge);
    }

    @Override
    public Optional<Edge> findAt(final NanoId edgeId, final Instant timestamp) {
        final var timestampStr = timestamp.toString();
        return g.E().has("id", edgeId.id())
                .where(__.values("created").is(lte(timestampStr)))
                .where(__.or(__.not(__.has("expired")), __.values("expired").is(gt(timestampStr))))
                .order()
                .by("versionId", Order.desc)
                .limit(1)
                .tryNext()
                .map(this::edgeToEdge);
    }

    @Override
    public boolean delete(final NanoId edgeId) {
        final var count = g.E().has("id", edgeId.id()).count().next();
        if (count > 0) {
            g.E().has("id", edgeId.id()).drop().iterate();
            return true;
        }
        return false;
    }

    @Override
    public boolean expire(final NanoId elementId, final Instant expiredAt) {
        final var edges = g.E().has("id", elementId.id()).not(__.has("expired")).toList();
        if (!edges.isEmpty()) {
            edges.forEach(e -> e.property("expired", expiredAt.toString()));
            return true;
        }
        return false;
    }

    private Vertex findOrCreateVertexForNode(final Node node) {
        final var existing = g.V().has("id", node.locator().id().id()).tryNext();
        if (existing.isPresent()) {
            return existing.get();
        }
        // Create vertex if it doesn't exist
        nodeRepository.save(node);
        return g.V().has("id", node.locator().id().id()).next();
    }

    private Edge edgeToEdge(final org.apache.tinkerpop.gremlin.structure.Edge tinkerpopEdge) {
        final var id = new NanoId(tinkerpopEdge.value("id"));
        final int versionId = tinkerpopEdge.value("versionId");
        final var created = Instant.parse(tinkerpopEdge.value("created"));

        Optional<Instant> expired = Optional.empty();
        if (tinkerpopEdge.property("expired").isPresent()) {
            expired = Optional.of(Instant.parse(tinkerpopEdge.value("expired")));
        }

        final var properties = tinkerpopEdge.keys().stream()
                .filter(key -> !List.of(
                                "id",
                                "versionId",
                                "sourceId",
                                "sourceVersionId",
                                "targetId",
                                "targetVersionId",
                                "created",
                                "expired")
                        .contains(key))
                .collect(Collectors.toMap(Function.identity(), tinkerpopEdge::<Object>value));

        final var data = serde.deserialize(properties);
        final var locator = new Locator(id, versionId);

        // Get source and target nodes using the stored version information
        final var sourceId = new NanoId(tinkerpopEdge.value("sourceId"));
        final var sourceVersionId = (Integer) tinkerpopEdge.value("sourceVersionId");
        final var targetId = new NanoId(tinkerpopEdge.value("targetId"));
        final var targetVersionId = (Integer) tinkerpopEdge.value("targetVersionId");

        final var source =
                nodeRepository.find(new Locator(sourceId, sourceVersionId)).orElseThrow();
        final var target =
                nodeRepository.find(new Locator(targetId, targetVersionId)).orElseThrow();

        return new SimpleEdge(locator, source, target, data, created, expired);
    }
}
