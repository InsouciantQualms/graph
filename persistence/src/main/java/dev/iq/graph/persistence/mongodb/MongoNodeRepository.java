package dev.iq.graph.persistence.mongodb;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import dev.iq.common.fp.Io;
import dev.iq.common.persist.VersionedRepository;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.serde.JsonSerde;
import dev.iq.graph.model.serde.Serde;
import dev.iq.graph.model.simple.SimpleNode;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.descending;

/**
 * MongoDB implementation of NodeRepository using JsonSerde for data serialization.
 */
public final class MongoNodeRepository implements VersionedRepository<Node> {

    private final MongoCollection<Document> collection;
    private final Serde<String> serde = new JsonSerde();

    public MongoNodeRepository(final MongoDatabase database) {
        collection = database.getCollection("nodes");
    }

    @Override
    public Node save(final Node node) {
        return Io.withReturn(() -> {
            final var document = new Document()
                .append("_id", node.locator().id().id() + ':' + node.locator().version())
                .append("id", node.locator().id().id())
                .append("versionId", node.locator().version())
                .append("created", node.created().toString())
                .append("data", (serde.serialize(node.data())));

            node.expired().ifPresent(expired ->
                document.append("expired", expired.toString()));

            collection.insertOne(document);
            return node;
        });
    }

    @Override
    public Optional<Node> findActive(final NanoId nodeId) {
        final var document = collection.find(
            and(eq("id", nodeId.id()), not(exists("expired")))
        ).sort(descending("versionId")).first();

        return Optional.ofNullable(document).map(this::documentToNode);
    }

    @Override
    public List<Node> findAll(final NanoId nodeId) {
        final var documents = collection.find(eq("id", nodeId.id()))
            .sort(ascending("versionId"));

        final var nodes = new ArrayList<Node>();
        for (final var document : documents) {
            nodes.add(documentToNode(document));
        }
        return nodes;
    }

    @Override
    public Optional<Node> find(final Locator locator) {
        final var document = collection.find(
            and(eq("id", locator.id().id()), eq("versionId", locator.version()))
        ).first();

        return Optional.ofNullable(document).map(this::documentToNode);
    }

    @Override
    public Optional<Node> findAt(final NanoId nodeId, final Instant timestamp) {
        final var timestampStr = timestamp.toString();
        final var document = collection.find(
            and(
                eq("id", nodeId.id()),
                lte("created", timestampStr),
                or(not(exists("expired")), gt("expired", timestampStr))
            )
        ).sort(descending("versionId")).first();

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
            new Document("$set", new Document("expired", expiredAt.toString()))
        );
        return result.getModifiedCount() > 0;
    }

    private Node documentToNode(final Document document) {
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

            final var locator = new Locator(id, versionId);
            return new SimpleNode(locator, List.of(), data, created, expired);
        });
    }
}