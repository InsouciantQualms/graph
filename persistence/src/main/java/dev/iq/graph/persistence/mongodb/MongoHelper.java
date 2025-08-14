/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb;

import dev.iq.common.fp.Io;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.bson.Document;

/**
 * Utility class containing common MongoDB operations for graph persistence.
 */
final class MongoHelper {

    /**
     * Private constructor for utility class.
     */
    private MongoHelper() {}

    /**
     * Formats an Instant to ISO-8601 string with millisecond precision.
     * MongoDB schema expects timestamps with up to 3 decimal places.
     */
    private static String formatTimestamp(final Instant instant) {
        // Truncate to milliseconds to match MongoDB schema validation
        return instant.truncatedTo(ChronoUnit.MILLIS).toString();
    }

    /**
     * Creates a base document with common fields for versioned entities.
     */
    static Document createBaseDocument(
            final Locator locator, final String type, final Instant created, final String serializedData) {

        return new Document()
                .append("_id", locator.id().code() + ':' + locator.version())
                .append("id", locator.id().code())
                .append("versionId", locator.version())
                .append("type", type)
                .append("created", formatTimestamp(created))
                .append("data", serializedData);
    }

    /**
     * Adds expiry field to a document if expired timestamp is present.
     */
    static void addExpiryToDocument(final Document document, final Optional<Instant> expired) {

        expired.ifPresent(expiredTime -> document.append("expired", formatTimestamp(expiredTime)));
    }

    /**
     * Extracts common versioned entity fields from a MongoDB document.
     */
    static VersionedDocumentData extractVersionedData(final Document document) {

        return Io.withReturn(() -> {
            final var id = NanoId.from(document.getString("id"));
            final var versionId = document.getInteger("versionId");
            final var type = document.getString("type");
            final var created = Instant.parse(document.getString("created"));

            Optional<Instant> expired = Optional.empty();
            final var expiredStr = document.getString("expired");
            if (expiredStr != null) {
                expired = Optional.of(Instant.parse(expiredStr));
            }

            final var json = document.getString("data");
            final var locator = new Locator(id, versionId);

            return new VersionedDocumentData(locator, type, created, expired, json);
        });
    }

    /**
     * Converts a collection of string IDs to NanoId list.
     */
    static List<NanoId> convertToNanoIdList(final Iterable<String> stringIds) {

        return Io.withReturn(() -> StreamSupport.stream(stringIds.spliterator(), false)
                .map(NanoId::from)
                .toList());
    }

    /**
     * Serializes a set of component locators to a list of documents.
     */
    static List<Document> serializeComponents(final Set<Locator> components) {
        final var list = new ArrayList<Document>();
        for (final var locator : components) {
            list.add(new Document().append("id", locator.id().code()).append("versionId", locator.version()));
        }
        return list;
    }

    /**
     * Deserializes a list of documents to a set of component locators.
     */
    static Set<Locator> deserializeComponents(final List<Document> documents) {
        final var components = new HashSet<Locator>();
        if (documents != null) {
            for (final var doc : documents) {
                final var id = NanoId.from(doc.getString("id"));
                final var versionId = doc.getInteger("versionId");
                components.add(new Locator(id, versionId));
            }
        }
        return components;
    }

    /**
     * Record containing extracted versioned document data.
     */
    record VersionedDocumentData(
            Locator locator, String type, Instant created, Optional<Instant> expired, String serializedData) {}
}
