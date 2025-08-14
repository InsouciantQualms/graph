/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.tinkerpop;

import dev.iq.common.fp.Io;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.common.version.Uid;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.simple.SimpleComponent;
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
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.springframework.stereotype.Repository;

/**
 * Tinkerpop implementation of ComponentRepository.
 *
 * This implementation stores components as vertices in the graph with label "component".
 * When used with the model layer, TinkerPop should use GraphComponentStrategy which
 * stores components as special manifest nodes, making this repository compatible with
 * the graph-based component storage approach.
 */
@Repository("tinkerpopComponentRepository")
public final class TinkerpopComponentRepository implements ExtendedVersionedRepository<Component> {

    private final Graph graph;
    private final GraphTraversalSource traversal;
    private final TinkerpopNodeRepository nodeRepository;
    private final TinkerpopEdgeRepository edgeRepository;
    private final Serde<Map<String, Object>> serde = new PropertiesSerde();

    public TinkerpopComponentRepository(
            final Graph graph,
            final TinkerpopNodeRepository nodeRepository,
            final TinkerpopEdgeRepository edgeRepository) {
        this.graph = graph;
        traversal = graph.traversal();
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    @Override
    public Component save(final Component component) {
        return Io.withReturn(() -> {
            final var vertex = graph.addVertex("component");

            vertex.property("id", component.locator().id().code());
            vertex.property("version", component.locator().version());
            vertex.property("created", component.created().toString());
            component.expired().ifPresent(exp -> vertex.property("expired", exp.toString()));

            // Store type
            vertex.property("type", component.type().code());

            // Store data properties directly on the vertex
            final var properties = serde.serialize(component.data());
            properties.forEach(vertex::property);

            return component;
        });
    }

    @Override
    public Optional<Component> findActive(final Uid id) {
        return Io.withReturn(() -> traversal
                .V()
                .hasLabel("component")
                .has("id", id.code())
                .not(__.has("expired"))
                .order()
                .by("version", Order.desc)
                .limit(1)
                .tryNext()
                .map(this::vertexToComponent));
    }

    @Override
    public Optional<Component> findAt(final Uid id, final Instant timestamp) {
        return Io.withReturn(() -> {
            final var timestampStr = timestamp.toString();
            return traversal
                    .V()
                    .hasLabel("component")
                    .has("id", id.code())
                    .where(__.values("created").is(P.lte(timestampStr)))
                    .where(__.or(__.not(__.has("expired")), __.values("expired").is(P.gt(timestampStr))))
                    .order()
                    .by("version", Order.desc)
                    .limit(1)
                    .tryNext()
                    .map(this::vertexToComponent);
        });
    }

    @Override
    public Component find(final Locator locator) {
        return Io.withReturn(() -> traversal
                .V()
                .hasLabel("component")
                .has("id", locator.id().code())
                .has("version", locator.version())
                .tryNext()
                .map(this::vertexToComponent)
                .orElseThrow(() -> new IllegalArgumentException("Component not found for locator: " + locator)));
    }

    @Override
    public List<Component> findAll(final Uid id) {
        return Io.withReturn(
                () -> traversal.V().hasLabel("component").has("id", id.code()).order().by("version").toList().stream()
                        .map(this::vertexToComponent)
                        .toList());
    }

    @Override
    public List<Component> findVersions(final Uid id) {
        return findAll(id);
    }

    @Override
    public boolean expire(final Uid id, final Instant expiredAt) {
        return Io.withReturn(() -> {
            final var vertices = traversal
                    .V()
                    .hasLabel("component")
                    .has("id", id.code())
                    .not(__.has("expired"))
                    .toList();
            if (!vertices.isEmpty()) {
                vertices.forEach(v -> v.property("expired", expiredAt.toString()));
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean delete(final Uid id) {
        return Io.withReturn(() -> {
            final var count = traversal
                    .V()
                    .hasLabel("component")
                    .has("id", id.code())
                    .count()
                    .next();
            if (count > 0) {
                // First delete all edges
                traversal
                        .V()
                        .hasLabel("component")
                        .has("id", id.code())
                        .bothE()
                        .drop()
                        .iterate();

                // Then delete the vertices
                traversal.V().hasLabel("component").has("id", id.code()).drop().iterate();
                return true;
            }
            return false;
        });
    }

    @Override
    public List<NanoId> allIds() {
        return Io.withReturn(() -> traversal.V().hasLabel("component").values("id").dedup().toList().stream()
                .map(id -> NanoId.from((String) id))
                .toList());
    }

    @Override
    public List<NanoId> allActiveIds() {
        return Io.withReturn(
                () -> traversal.V().hasLabel("component").not(__.has("expired")).values("id").dedup().toList().stream()
                        .map(id -> NanoId.from((String) id))
                        .toList());
    }

    private Component vertexToComponent(final Vertex vertex) {
        final var id = NanoId.from(vertex.value("id"));
        final var version = vertex.<Integer>value("version");
        final var locator = new Locator(id, version);
        final var created = Instant.parse(vertex.value("created"));

        final var expired = vertex.property("expired").isPresent()
                ? Optional.of(Instant.parse(vertex.value("expired")))
                : Optional.<Instant>empty();

        // Reconstruct data from vertex properties
        final var properties = vertex.keys().stream()
                .filter(key ->
                        !List.of("id", "version", "type", "created", "expired").contains(key))
                .collect(Collectors.toMap(Function.identity(), vertex::<Object>value));
        final var data = serde.deserialize(properties);
        final var type = new SimpleType(vertex.value("type"));

        return new SimpleComponent(locator, type, data, created, expired);
    }
}
