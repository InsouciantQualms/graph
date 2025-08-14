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
import dev.iq.graph.model.Component;
import dev.iq.graph.model.simple.SimpleComponent;
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
 * MongoDB implementation of ComponentRepository using JsonSerde for data serialization.
 */
@Repository("mongoComponentRepository")
public final class MongoComponentRepository implements ExtendedVersionedRepository<Component> {

    private final MongoCollection<Document> collection;
    private final Serde<String> serde = new JsonSerde();

    public MongoComponentRepository(final MongoDatabase database) {
        collection = database.getCollection("components");
    }

    @Override
    public Component save(final Component component) {
        return Io.withReturn(() -> {
            final var document = MongoHelper.createBaseDocument(
                    component.locator(),
                    component.type().code(),
                    component.created(),
                    serde.serialize(component.data()));
            MongoHelper.addExpiryToDocument(document, component.expired());

            collection.insertOne(document);
            return component;
        });
    }

    @Override
    public Optional<Component> findActive(final Uid componentId) {
        final var document = collection
                .find(and(eq("id", componentId.code()), not(exists("expired"))))
                .sort(descending("versionId"))
                .first();

        return Optional.ofNullable(document).map(this::documentToComponent);
    }

    @Override
    public List<Component> findAll(final Uid componentId) {
        final var documents = collection.find(eq("id", componentId.code())).sort(ascending("versionId"));

        return StreamSupport.stream(documents.spliterator(), false)
                .map(this::documentToComponent)
                .toList();
    }

    @Override
    public List<Component> findVersions(final Uid componentId) {
        return findAll(componentId);
    }

    @Override
    public Component find(final Locator locator) {
        final var document = collection
                .find(and(eq("id", locator.id().code()), eq("versionId", locator.version())))
                .first();

        return Optional.ofNullable(document)
                .map(this::documentToComponent)
                .orElseThrow(() -> new IllegalArgumentException("Component not found for locator: " + locator));
    }

    @Override
    public Optional<Component> findAt(final Uid componentId, final Instant timestamp) {
        final var timestampStr = timestamp.truncatedTo(ChronoUnit.MILLIS).toString();
        final var document = collection
                .find(and(
                        eq("id", componentId.code()),
                        lte("created", timestampStr),
                        or(not(exists("expired")), gt("expired", timestampStr))))
                .sort(descending("versionId"))
                .first();

        return Optional.ofNullable(document).map(this::documentToComponent);
    }

    @Override
    public boolean delete(final Uid componentId) {
        final var result = collection.deleteMany(eq("id", componentId.code()));
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

    private Component documentToComponent(final Document document) {
        return Io.withReturn(() -> {
            final var versionedData = MongoHelper.extractVersionedData(document);
            final var data = serde.deserialize(versionedData.serializedData());
            final var type = new SimpleType(versionedData.type());

            return new SimpleComponent(
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
