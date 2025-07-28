/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb.schemas;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ValidationOptions;
import org.bson.Document;

/**
 * MongoDB JSON Schema for Edge collection.
 */
public final class EdgeSchema {

    private EdgeSchema() {}

    public static final String COLLECTION_NAME = "edges";

    public static final Document SCHEMA = new Document(
            "$jsonSchema",
            new Document()
                    .append("bsonType", "object")
                    .append(
                            "required",
                            java.util.Arrays.asList(
                                    "_id",
                                    "id",
                                    "versionId",
                                    "type",
                                    "created",
                                    "data",
                                    "sourceId",
                                    "sourceVersionId",
                                    "targetId",
                                    "targetVersionId"))
                    .append(
                            "properties",
                            new Document()
                                    .append(
                                            "_id",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append("description", "Composite key: id:versionId"))
                                    .append(
                                            "id",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append("pattern", "^[0-9a-zA-Z_-]{21}$")
                                                    .append("description", "NanoId of the edge"))
                                    .append(
                                            "versionId",
                                            new Document()
                                                    .append("bsonType", "int")
                                                    .append("minimum", 1)
                                                    .append("description", "Version number of the edge"))
                                    .append(
                                            "type",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append("minLength", 1)
                                                    .append("description", "Type code of the edge"))
                                    .append(
                                            "created",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append(
                                                            "pattern",
                                                            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z?$")
                                                    .append("description", "ISO 8601 timestamp when edge was created"))
                                    .append(
                                            "expired",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append(
                                                            "pattern",
                                                            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z?$")
                                                    .append(
                                                            "description",
                                                            "ISO 8601 timestamp when edge expired (optional)"))
                                    .append(
                                            "data",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append("description", "Serialized JSON data of the edge"))
                                    .append(
                                            "sourceId",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append("pattern", "^[0-9a-zA-Z_-]{21}$")
                                                    .append("description", "NanoId of the source node"))
                                    .append(
                                            "sourceVersionId",
                                            new Document()
                                                    .append("bsonType", "int")
                                                    .append("minimum", 1)
                                                    .append("description", "Version number of the source node"))
                                    .append(
                                            "targetId",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append("pattern", "^[0-9a-zA-Z_-]{21}$")
                                                    .append("description", "NanoId of the target node"))
                                    .append(
                                            "targetVersionId",
                                            new Document()
                                                    .append("bsonType", "int")
                                                    .append("minimum", 1)
                                                    .append("description", "Version number of the target node")))
                    .append("additionalProperties", false));

    public static void createCollection(final MongoDatabase database) {
        if (!collectionExists(database, COLLECTION_NAME)) {
            database.createCollection(
                    COLLECTION_NAME,
                    new CreateCollectionOptions()
                            .validationOptions(new ValidationOptions()
                                    .validator(SCHEMA)
                                    .validationLevel(com.mongodb.client.model.ValidationLevel.STRICT)
                                    .validationAction(com.mongodb.client.model.ValidationAction.ERROR)));

            // Create indexes for graph operations
            final var collection = database.getCollection(COLLECTION_NAME);
            collection.createIndex(Indexes.ascending("sourceId"), new IndexOptions().name("sourceId_1"));
            collection.createIndex(Indexes.ascending("targetId"), new IndexOptions().name("targetId_1"));
            collection.createIndex(
                    new Document().append("sourceId", 1).append("expired", 1),
                    new IndexOptions().name("sourceId_expired"));
            collection.createIndex(
                    new Document().append("targetId", 1).append("expired", 1),
                    new IndexOptions().name("targetId_expired"));
        }
    }

    private static boolean collectionExists(final MongoDatabase database, final String collectionName) {
        for (final String name : database.listCollectionNames()) {
            if (name.equals(collectionName)) {
                return true;
            }
        }
        return false;
    }
}
