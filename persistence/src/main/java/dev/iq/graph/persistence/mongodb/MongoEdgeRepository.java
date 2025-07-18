/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.not;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.descending;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import dev.iq.common.fp.Io;
import dev.iq.common.persist.VersionedRepository;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.serde.JsonSerde;
import dev.iq.graph.model.serde.Serde;
import dev.iq.graph.model.simple.SimpleEdge;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.Document;

/**
 * MongoDB implementation of EdgeRepository using JsonSerde for data serialization.
 */
public final class MongoEdgeRepository implements VersionedRepository<Edge> {

    private final MongoCollection<Document> collection;
    private final Serde<String> serde = new JsonSerde();
    private final MongoNodeRepository nodeRepository;

    public MongoEdgeRepository(final MongoDatabase database, final MongoNodeRepository nodeRepository) {
        collection = database.getCollection("edges");
        this.nodeRepository = nodeRepository;
    }

    @Override
    public Edge save(final Edge edge) {
        return Io.withReturn(() -> {
            final var document = new Document()
                    .append(
                            "_id",
                            edge.locator().id().id() + ':' + edge.locator().version())
                    .append("id", edge.locator().id().id())
                    .append("versionId", edge.locator().version())
                    .append("sourceId", edge.source().locator().id().id())
                    .append("sourceVersionId", edge.source().locator().version())
                    .append("targetId", edge.target().locator().id().id())
                    .append("targetVersionId", edge.target().locator().version())
                    .append("created", edge.created().toString())
                    .append("data", serde.serialize(edge.data()));

            edge.expired().ifPresent(expired -> document.append("expired", expired.toString()));

            collection.insertOne(document);
            return edge;
        });
    }

    @Override
    public Optional<Edge> findActive(final NanoId edgeId) {
        final var document = collection
                .find(and(eq("id", edgeId.id()), not(exists("expired"))))
                .sort(descending("versionId"))
                .first();

        return Optional.ofNullable(document).map(this::documentToEdge);
    }

    @Override
    public List<Edge> findAll(final NanoId edgeId) {
        final var documents = collection.find(eq("id", edgeId.id())).sort(ascending("versionId"));

        final var edges = new ArrayList<Edge>();
        for (final var document : documents) {
            edges.add(documentToEdge(document));
        }
        return edges;
    }

    @Override
    public Optional<Edge> find(final Locator locator) {
        final var document = collection
                .find(and(eq("id", locator.id().id()), eq("versionId", locator.version())))
                .first();

        return Optional.ofNullable(document).map(this::documentToEdge);
    }

    @Override
    public Optional<Edge> findAt(final NanoId edgeId, final Instant timestamp) {
        final var timestampStr = timestamp.toString();
        final var document = collection
                .find(and(
                        eq("id", edgeId.id()),
                        lte("created", timestampStr),
                        or(not(exists("expired")), gt("expired", timestampStr))))
                .sort(descending("versionId"))
                .first();

        return Optional.ofNullable(document).map(this::documentToEdge);
    }

    @Override
    public boolean delete(final NanoId edgeId) {
        final var result = collection.deleteMany(eq("id", edgeId.id()));
        return result.getDeletedCount() > 0;
    }

    @Override
    public boolean expire(final NanoId elementId, final Instant expiredAt) {
        final var result = collection.updateMany(
                and(eq("id", elementId.id()), not(exists("expired"))),
                new Document("$set", new Document("expired", expiredAt.toString())));
        return result.getModifiedCount() > 0;
    }

    private Edge documentToEdge(final Document document) {
        return Io.withReturn(() -> {
            final var id = new NanoId(document.getString("id"));
            final var versionId = document.getInteger("versionId");
            final var created = Instant.parse(document.getString("created"));

            Optional<Instant> expired = Optional.empty();
            final var expiredStr = document.getString("expired");
            if (expiredStr != null) {
                expired = Optional.of(Instant.parse(expiredStr));
            }

            final var json = document.getString("data");
            final var data = serde.deserialize(json);

            // Retrieve source and target nodes
            final var sourceId = new NanoId(document.getString("sourceId"));
            final var sourceVersionId = document.getInteger("sourceVersionId");
            final var targetId = new NanoId(document.getString("targetId"));
            final var targetVersionId = document.getInteger("targetVersionId");

            final var sourceLocator = new Locator(sourceId, sourceVersionId);
            final var targetLocator = new Locator(targetId, targetVersionId);

            final var source = nodeRepository
                    .find(sourceLocator)
                    .orElseThrow(() -> new IllegalStateException("Source node not found: " + sourceLocator));
            final var target = nodeRepository
                    .find(targetLocator)
                    .orElseThrow(() -> new IllegalStateException("Target node not found: " + targetLocator));

            final var locator = new Locator(id, versionId);
            return new SimpleEdge(locator, source, target, data, created, expired);
        });
    }
}
