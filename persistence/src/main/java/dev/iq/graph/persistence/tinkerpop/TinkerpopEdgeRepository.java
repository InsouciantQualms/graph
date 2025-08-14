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
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleType;
import dev.iq.graph.persistence.ExtendedVersionedRepository;
import dev.iq.graph.persistence.serde.PropertiesSerde;
import dev.iq.graph.persistence.serde.Serde;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * Tinkerpop implementation of EdgeRepository.
 */
@Repository("tinkerpopEdgeRepository")
public final class TinkerpopEdgeRepository implements ExtendedVersionedRepository<Edge> {

    private final GraphTraversalSource traversal;
    private final TinkerpopNodeRepository nodeRepository;
    private final Serde<Map<String, Object>> serde = new PropertiesSerde();

    public TinkerpopEdgeRepository(final @NotNull Graph graph, final TinkerpopNodeRepository nodeRepository) {
        traversal = graph.traversal();
        this.nodeRepository = nodeRepository;
    }

    @Override
    public Edge save(final @NotNull Edge edge) {
        final var sourceVertex = findOrCreateVertexForNode(edge.source());
        final var targetVertex = findOrCreateVertexForNode(edge.target());

        final var tinkerpopEdge = sourceVertex.addEdge("edge", targetVertex);
        tinkerpopEdge.property("id", edge.locator().id().code());
        tinkerpopEdge.property("versionId", edge.locator().version());
        tinkerpopEdge.property("type", edge.type().code());
        tinkerpopEdge.property("sourceId", edge.source().locator().id().code());
        tinkerpopEdge.property("sourceVersionId", edge.source().locator().version());
        tinkerpopEdge.property("targetId", edge.target().locator().id().code());
        tinkerpopEdge.property("targetVersionId", edge.target().locator().version());
        tinkerpopEdge.property("created", edge.created().toString());
        edge.expired().ifPresent(expired -> tinkerpopEdge.property("expired", expired.toString()));

        final var properties = serde.serialize(edge.data());
        properties.forEach(tinkerpopEdge::property);

        // Save components
        saveComponents(tinkerpopEdge, edge.components());

        return edge;
    }

    @Override
    public Optional<Edge> findActive(final Uid edgeId) {
        final var edgeOpt =
                traversal.E().has("id", edgeId.code()).not(__.has("expired")).tryNext();
        return edgeOpt.map(this::edgeToEdge);
    }

    @Override
    public List<Edge> findAll(final Uid edgeId) {
        return traversal.E().has("id", edgeId.code()).order().by("versionId").toList().stream()
                .map(this::edgeToEdge)
                .toList();
    }

    @Override
    public List<Edge> findVersions(final Uid edgeId) {
        return findAll(edgeId);
    }

    @Override
    public Edge find(final Locator locator) {
        final var edgeOpt = traversal
                .E()
                .has("id", locator.id().code())
                .has("versionId", locator.version())
                .tryNext();
        return edgeOpt.map(this::edgeToEdge)
                .orElseThrow(() -> new IllegalArgumentException("Edge not found for locator: " + locator));
    }

    @Override
    public Optional<Edge> findAt(final Uid edgeId, final Instant timestamp) {
        final var timestampStr = timestamp.toString();
        return traversal
                .E()
                .has("id", edgeId.code())
                .where(__.values("created").is(lte(timestampStr)))
                .where(__.or(__.not(__.has("expired")), __.values("expired").is(gt(timestampStr))))
                .order()
                .by("versionId", Order.desc)
                .limit(1)
                .tryNext()
                .map(this::edgeToEdge);
    }

    @Override
    public boolean delete(final Uid edgeId) {
        final var count = traversal.E().has("id", edgeId.code()).count().next();
        if (count > 0) {
            traversal.E().has("id", edgeId.code()).drop().iterate();
            return true;
        }
        return false;
    }

    @Override
    public boolean expire(final Uid elementId, final Instant expiredAt) {
        final var edges =
                traversal.E().has("id", elementId.code()).not(__.has("expired")).toList();
        if (!edges.isEmpty()) {
            edges.forEach(e -> e.property("expired", expiredAt.toString()));
            return true;
        }
        return false;
    }

    @Override
    public List<NanoId> allIds() {
        return traversal.E().values("id").dedup().toList().stream()
                .map(id -> NanoId.from((String) id))
                .toList();
    }

    @Override
    public List<NanoId> allActiveIds() {
        return traversal.E().not(__.has("expired")).values("id").dedup().toList().stream()
                .map(id -> NanoId.from((String) id))
                .toList();
    }

    private Vertex findOrCreateVertexForNode(final Node node) {
        final var existing = traversal.V().has("id", node.locator().id().code()).tryNext();
        if (existing.isPresent()) {
            return existing.get();
        }
        // Create vertex if it doesn't exist
        nodeRepository.save(node);
        return traversal.V().has("id", node.locator().id().code()).next();
    }

    private Edge edgeToEdge(final org.apache.tinkerpop.gremlin.structure.Edge tinkerpopEdge) {
        final var id = NanoId.from(tinkerpopEdge.value("id"));
        final int versionId = tinkerpopEdge.value("versionId");
        final var type = new SimpleType(tinkerpopEdge.value("type"));
        final var created = Instant.parse(tinkerpopEdge.value("created"));

        Optional<Instant> expired = Optional.empty();
        if (tinkerpopEdge.property("expired").isPresent()) {
            expired = Optional.of(Instant.parse(tinkerpopEdge.value("expired")));
        }

        final var properties = tinkerpopEdge.keys().stream()
                .filter(key -> !List.of(
                                "id",
                                "versionId",
                                "type",
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
        final var sourceId = NanoId.from(tinkerpopEdge.value("sourceId"));
        final var sourceVersionId = (Integer) tinkerpopEdge.value("sourceVersionId");
        final var targetId = NanoId.from(tinkerpopEdge.value("targetId"));
        final var targetVersionId = (Integer) tinkerpopEdge.value("targetVersionId");

        final var source = nodeRepository.find(new Locator(sourceId, sourceVersionId));
        final var target = nodeRepository.find(new Locator(targetId, targetVersionId));
        final var components = loadComponents(tinkerpopEdge);

        return new SimpleEdge(locator, type, source, target, data, components, created, expired);
    }

    private void saveComponents(final org.apache.tinkerpop.gremlin.structure.Edge edge, final Set<Locator> components) {
        if (components.isEmpty()) {
            return;
        }

        // Store components as a serialized property - each component as "componentId:versionId"
        final var componentStrings = components.stream()
                .map(loc -> loc.id().code() + ":" + loc.version())
                .toList();
        edge.property("components", String.join(",", componentStrings));
    }

    private Set<Locator> loadComponents(final org.apache.tinkerpop.gremlin.structure.Edge edge) {
        final var components = new HashSet<Locator>();

        if (edge.property("components").isPresent()) {
            final var componentsStr = edge.<String>value("components");
            if (!componentsStr.isEmpty()) {
                final var componentPairs = componentsStr.split(",");
                for (final var pair : componentPairs) {
                    final var parts = pair.split(":");
                    if (parts.length == 2) {
                        try {
                            final var id = NanoId.from(parts[0]);
                            final var version = Integer.parseInt(parts[1]);
                            components.add(new Locator(id, version));
                        } catch (final NumberFormatException ignored) {
                            // Skip invalid component references
                        }
                    }
                }
            }
        }

        return components;
    }
}
