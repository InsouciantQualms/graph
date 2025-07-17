/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.descending;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import dev.iq.common.fp.Io;
import dev.iq.common.persist.VersionedRepository;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.serde.JsonSerde;
import dev.iq.graph.model.serde.Serde;
import dev.iq.graph.model.simple.SimpleComponent;

/**
 * MongoDB implementation of ComponentRepository using JsonSerde for data serialization.
 */
public final class MongoComponentRepository implements VersionedRepository<Component> {

    private final MongoCollection<Document> collection;
    private final MongoCollection<Document> elementsCollection;
    private final Serde<String> serde = new JsonSerde();
    private final MongoNodeRepository nodeRepository;
    private final MongoEdgeRepository edgeRepository;

    public MongoComponentRepository(
            final MongoDatabase database,
            final MongoNodeRepository nodeRepository,
            final MongoEdgeRepository edgeRepository) {
        collection = database.getCollection("components");
        elementsCollection = database.getCollection("component_elements");
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    @Override
    public Component save(final Component component) {
        return Io.withReturn(() -> {
            final var document = new Document()
                    .append(
                            "_id",
                            component.locator().id().id()
                                    + ':'
                                    + component.locator().version())
                    .append("id", component.locator().id().id())
                    .append("versionId", component.locator().version())
                    .append("created", component.created().toString())
                    .append("data", serde.serialize(component.data()));

            component.expired().ifPresent(expired -> document.append("expired", expired.toString()));

            collection.insertOne(document);

            // Save component elements relationships
            for (final var element : component.elements()) {
                final var elementDoc = new Document()
                        .append("componentId", component.locator().id().id())
                        .append("componentVersionId", component.locator().version())
                        .append("elementId", element.locator().id().id())
                        .append("elementVersionId", element.locator().version())
                        .append("elementType", (element instanceof Node) ? "node" : "edge");

                elementsCollection.insertOne(elementDoc);
            }

            return component;
        });
    }

    @Override
    public Optional<Component> findActive(final NanoId componentId) {
        final var document = collection
                .find(and(eq("id", componentId.id()), not(exists("expired"))))
                .sort(descending("versionId"))
                .first();

        return Optional.ofNullable(document).map(this::documentToComponent);
    }

    @Override
    public List<Component> findAll(final NanoId componentId) {
        final var documents = collection.find(eq("id", componentId.id())).sort(ascending("versionId"));

        final var components = new ArrayList<Component>();
        for (final var document : documents) {
            components.add(documentToComponent(document));
        }
        return components;
    }

    @Override
    public Optional<Component> find(final Locator locator) {
        final var document = collection
                .find(and(eq("id", locator.id().id()), eq("versionId", locator.version())))
                .first();

        return Optional.ofNullable(document).map(this::documentToComponent);
    }

    @Override
    public Optional<Component> findAt(final NanoId componentId, final Instant timestamp) {
        final var timestampStr = timestamp.toString();
        final var document = collection
                .find(and(
                        eq("id", componentId.id()),
                        lte("created", timestampStr),
                        or(not(exists("expired")), gt("expired", timestampStr))))
                .sort(descending("versionId"))
                .first();

        return Optional.ofNullable(document).map(this::documentToComponent);
    }

    @Override
    public boolean delete(final NanoId componentId) {
        final var componentResult = collection.deleteMany(eq("id", componentId.id()));
        final var elementsResult = elementsCollection.deleteMany(eq("componentId", componentId.id()));
        return componentResult.getDeletedCount() > 0;
    }

    @Override
    public boolean expire(final NanoId elementId, final Instant expiredAt) {
        final var result = collection.updateMany(
                and(eq("id", elementId.id()), not(exists("expired"))),
                new Document("$set", new Document("expired", expiredAt.toString())));
        return result.getModifiedCount() > 0;
    }

    private Component documentToComponent(final Document document) {
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

            // Load component elements
            final var elements = new ArrayList<Element>();
            final var elementDocs =
                    elementsCollection.find(and(eq("componentId", id.id()), eq("componentVersionId", versionId)));

            for (final var elementDoc : elementDocs) {
                final var elementId = new NanoId(elementDoc.getString("elementId"));
                final var elementVersionId = elementDoc.getInteger("elementVersionId");
                final var elementLocator = new Locator(elementId, elementVersionId);
                final var elementType = elementDoc.getString("elementType");

                if ("node".equals(elementType)) {
                    nodeRepository.find(elementLocator).ifPresent(elements::add);
                } else if ("edge".equals(elementType)) {
                    edgeRepository.find(elementLocator).ifPresent(elements::add);
                }
            }

            final var locator = new Locator(id, versionId);
            return new SimpleComponent(locator, elements, data, created, expired);
        });
    }
}
