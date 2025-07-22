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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
     * Creates a base document with common fields for versioned entities.
     */
    static Document createBaseDocument(final Locator locator, final Instant created, final String serializedData) {

        return new Document()
                .append("_id", locator.id().id() + ':' + locator.version())
                .append("id", locator.id().id())
                .append("versionId", locator.version())
                .append("created", created.toString())
                .append("data", serializedData);
    }

    /**
     * Adds expiry field to a document if expired timestamp is present.
     */
    static void addExpiryToDocument(final Document document, final Optional<Instant> expired) {

        expired.ifPresent(expiredTime -> document.append("expired", expiredTime.toString()));
    }

    /**
     * Extracts common versioned entity fields from a MongoDB document.
     */
    static VersionedDocumentData extractVersionedData(final Document document) {

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
            final var locator = new Locator(id, versionId);

            return new VersionedDocumentData(locator, created, expired, json);
        });
    }

    /**
     * Converts a collection of string IDs to NanoId list.
     */
    static List<NanoId> convertToNanoIdList(final Iterable<String> stringIds) {

        return Io.withReturn(() -> {
            final var result = new ArrayList<NanoId>();
            for (final var id : stringIds) {
                result.add(new NanoId(id));
            }
            return result;
        });
    }

    /**
     * Record containing extracted versioned document data.
     */
    record VersionedDocumentData(Locator locator, Instant created, Optional<Instant> expired, String serializedData) {}

}
