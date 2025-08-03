/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb.schemas;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import java.util.Arrays;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 * MongoDB JSON Schema for Node collection.
 */
public final class NodeSchema {

    private NodeSchema() {}

    public static final String COLLECTION_NAME = "nodes";

    public static final Bson SCHEMA = new Document(
            "$jsonSchema",
            new Document()
                    .append("bsonType", "object")
                    .append("required", Arrays.asList("_id", "id", "versionId", "type", "created", "data"))
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
                                                    .append("description", "NanoId of the node"))
                                    .append(
                                            "versionId",
                                            new Document()
                                                    .append("bsonType", "int")
                                                    .append("minimum", 1)
                                                    .append("description", "Version number of the node"))
                                    .append(
                                            "type",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append("minLength", 1)
                                                    .append("description", "Type code of the node"))
                                    .append(
                                            "created",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append(
                                                            "pattern",
                                                            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z?$")
                                                    .append("description", "ISO 8601 timestamp when node was created"))
                                    .append(
                                            "expired",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append(
                                                            "pattern",
                                                            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z?$")
                                                    .append(
                                                            "description",
                                                            "ISO 8601 timestamp when node expired (optional)"))
                                    .append(
                                            "data",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append("description", "Serialized JSON data of the node")))
                    .append("additionalProperties", false));

    public static void createCollection(final MongoDatabase database) {
        if (!collectionExists(database)) {
            database.createCollection(
                    COLLECTION_NAME,
                    new CreateCollectionOptions()
                            .validationOptions(new ValidationOptions()
                                    .validator(SCHEMA)
                                    .validationLevel(ValidationLevel.STRICT)
                                    .validationAction(ValidationAction.ERROR)));
        }
    }

    private static boolean collectionExists(final MongoDatabase database) {
        for (final var name : database.listCollectionNames()) {
            if (name.equals(NodeSchema.COLLECTION_NAME)) {
                return true;
            }
        }
        return false;
    }
}
