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
import dev.iq.common.version.Uid;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.model.simple.SimpleType;
import dev.iq.graph.persistence.ExtendedVersionedRepository;
import dev.iq.graph.persistence.serde.JsonSerde;
import dev.iq.graph.persistence.serde.Serde;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    public Optional<Node> findActive(final Uid nodeId) {
        final var document = collection
                .find(and(eq("id", nodeId.code()), not(exists("expired"))))
                .sort(descending("versionId"))
                .first();

        return Optional.ofNullable(document).map(this::documentToNode);
    }

    @Override
    public List<Node> findAll(final Uid nodeId) {
        final var documents = collection.find(eq("id", nodeId.code())).sort(ascending("versionId"));

        return StreamSupport.stream(documents.spliterator(), false)
                .map(this::documentToNode)
                .toList();
    }

    @Override
    public List<Node> findVersions(final Uid nodeId) {
        return findAll(nodeId);
    }

    @Override
    public Node find(final Locator locator) {
        final var document = collection
                .find(and(eq("id", locator.id().code()), eq("versionId", locator.version())))
                .first();

        return Optional.ofNullable(document)
                .map(this::documentToNode)
                .orElseThrow(() -> new IllegalArgumentException("Node not found for locator: " + locator));
    }

    @Override
    public Optional<Node> findAt(final Uid nodeId, final Instant timestamp) {
        final var timestampStr = timestamp.truncatedTo(ChronoUnit.MILLIS).toString();
        final var document = collection
                .find(and(
                        eq("id", nodeId.code()),
                        lte("created", timestampStr),
                        or(not(exists("expired")), gt("expired", timestampStr))))
                .sort(descending("versionId"))
                .first();

        return Optional.ofNullable(document).map(this::documentToNode);
    }

    @Override
    public boolean delete(final Uid nodeId) {
        final var result = collection.deleteMany(eq("id", nodeId.code()));
        return result.getDeletedCount() > 0;
    }

    @Override
    public boolean expire(final Uid elementId, final Instant expiredAt) {
        final var result = collection.updateMany(
                and(eq("id", elementId.code()), not(exists("expired"))),
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
                    versionedData.locator(), type, data, versionedData.created(), versionedData.expired());
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
