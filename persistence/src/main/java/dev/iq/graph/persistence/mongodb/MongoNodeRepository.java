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
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.serde.JsonSerde;
import dev.iq.graph.model.serde.Serde;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.model.simple.SimpleType;
import dev.iq.graph.persistence.ExtendedVersionedRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.bson.Document;
import org.springframework.stereotype.Repository;

/**
 * MongoDB implementation of NodeRepository using JsonSerde for data serialization.
 */
@Repository("mongoNodeRepository")
public final class MongoNodeRepository implements ExtendedVersionedRepository<Node> {

    private final MongoCollection<Document> collection;
    private final Serde<String> serde = new JsonSerde();

    public MongoNodeRepository(final MongoDatabase database) {
        collection = database.getCollection("nodes");
    }

    @Override
    public Node save(final Node node) {
        return Io.withReturn(() -> {
            final var document = MongoHelper.createBaseDocument(
                    node.locator(), node.type().code(), node.created(), serde.serialize(node.data()));
            MongoHelper.addExpiryToDocument(document, node.expired());

            collection.insertOne(document);
            return node;
        });
    }

    @Override
    public Optional<Node> findActive(final NanoId nodeId) {
        final var document = collection
                .find(and(eq("id", nodeId.id()), not(exists("expired"))))
                .sort(descending("versionId"))
                .first();

        return Optional.ofNullable(document).map(this::documentToNode);
    }

    @Override
    public List<Node> findAll(final NanoId nodeId) {
        final var documents = collection.find(eq("id", nodeId.id())).sort(ascending("versionId"));

        return StreamSupport.stream(documents.spliterator(), false)
                .map(this::documentToNode)
                .toList();
    }

    @Override
    public Optional<Node> find(final Locator locator) {
        final var document = collection
                .find(and(eq("id", locator.id().id()), eq("versionId", locator.version())))
                .first();

        return Optional.ofNullable(document).map(this::documentToNode);
    }

    @Override
    public Optional<Node> findAt(final NanoId nodeId, final Instant timestamp) {
        final var timestampStr = timestamp.truncatedTo(ChronoUnit.MILLIS).toString();
        final var document = collection
                .find(and(
                        eq("id", nodeId.id()),
                        lte("created", timestampStr),
                        or(not(exists("expired")), gt("expired", timestampStr))))
                .sort(descending("versionId"))
                .first();

        return Optional.ofNullable(document).map(this::documentToNode);
    }

    @Override
    public boolean delete(final NanoId nodeId) {
        final var result = collection.deleteMany(eq("id", nodeId.id()));
        return result.getDeletedCount() > 0;
    }

    @Override
    public boolean expire(final NanoId elementId, final Instant expiredAt) {
        final var result = collection.updateMany(
                and(eq("id", elementId.id()), not(exists("expired"))),
                new Document(
                        "$set",
                        new Document(
                                "expired",
                                expiredAt.truncatedTo(ChronoUnit.MILLIS).toString())));
        return result.getModifiedCount() > 0;
    }

    private Node documentToNode(final Document document) {
        return Io.withReturn(() -> {
            final var versionedData = MongoHelper.extractVersionedData(document);
            final var data = serde.deserialize(versionedData.serializedData());
            final var type = new SimpleType(versionedData.type());

            return new SimpleNode(
                    versionedData.locator(),
                    type,
                    List.of(),
                    data,
                    versionedData.created(),
                    versionedData.expired(),
                    new HashSet<>());
        });
    }

    @Override
    public List<NanoId> allIds() {
        return MongoHelper.convertToNanoIdList(collection.distinct("id", String.class));
    }

    @Override
    public List<NanoId> allActiveIds() {
        return MongoHelper.convertToNanoIdList(collection.distinct("id", not(exists("expired")), String.class));
    }
}
