/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb.schemas;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import java.util.Arrays;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 * MongoDB JSON Schema for Component collection and component_elements collection.
 */
public final class ComponentSchema {

    private ComponentSchema() {}

    public static final String COLLECTION_NAME = "components";
    public static final String ELEMENTS_COLLECTION_NAME = "component_elements";

    public static final Bson COMPONENT_SCHEMA = new Document(
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
                                                    .append("description", "NanoId of the component"))
                                    .append(
                                            "versionId",
                                            new Document()
                                                    .append("bsonType", "int")
                                                    .append("minimum", 1)
                                                    .append("description", "Version number of the component"))
                                    .append(
                                            "type",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append("enum", List.of("component"))
                                                    .append("description", "Type must be 'component'"))
                                    .append(
                                            "created",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append(
                                                            "pattern",
                                                            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z?$")
                                                    .append(
                                                            "description",
                                                            "ISO 8601 timestamp when component was created"))
                                    .append(
                                            "expired",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append(
                                                            "pattern",
                                                            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z?$")
                                                    .append(
                                                            "description",
                                                            "ISO 8601 timestamp when component expired (optional)"))
                                    .append(
                                            "data",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append("description", "Serialized JSON data of the component")))
                    .append("additionalProperties", false));

    public static final Bson ELEMENTS_SCHEMA = new Document(
            "$jsonSchema",
            new Document()
                    .append("bsonType", "object")
                    .append(
                            "required",
                            Arrays.asList(
                                    "componentId",
                                    "componentVersionId",
                                    "elementId",
                                    "elementVersionId",
                                    "elementType"))
                    .append(
                            "properties",
                            new Document()
                                    .append(
                                            "componentId",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append("pattern", "^[0-9a-zA-Z_-]{21}$")
                                                    .append("description", "NanoId of the component"))
                                    .append(
                                            "componentVersionId",
                                            new Document()
                                                    .append("bsonType", "int")
                                                    .append("minimum", 1)
                                                    .append("description", "Version number of the component"))
                                    .append(
                                            "elementId",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append("pattern", "^[0-9a-zA-Z_-]{21}$")
                                                    .append("description", "NanoId of the element (node or edge)"))
                                    .append(
                                            "elementVersionId",
                                            new Document()
                                                    .append("bsonType", "int")
                                                    .append("minimum", 1)
                                                    .append("description", "Version number of the element"))
                                    .append(
                                            "elementType",
                                            new Document()
                                                    .append("bsonType", "string")
                                                    .append("enum", Arrays.asList("node", "edge"))
                                                    .append("description", "Type of element: 'node' or 'edge'")))
                    .append("additionalProperties", false));

    public static void createCollections(final MongoDatabase database) {
        // Create components collection
        if (collectionExists(database, COLLECTION_NAME)) {
            database.createCollection(
                    COLLECTION_NAME,
                    new CreateCollectionOptions()
                            .validationOptions(new ValidationOptions()
                                    .validator(COMPONENT_SCHEMA)
                                    .validationLevel(ValidationLevel.STRICT)
                                    .validationAction(ValidationAction.ERROR)));
        }

        // Create component_elements collection
        if (collectionExists(database, ELEMENTS_COLLECTION_NAME)) {
            database.createCollection(
                    ELEMENTS_COLLECTION_NAME,
                    new CreateCollectionOptions()
                            .validationOptions(new ValidationOptions()
                                    .validator(ELEMENTS_SCHEMA)
                                    .validationLevel(ValidationLevel.STRICT)
                                    .validationAction(ValidationAction.ERROR)));

            // Create indexes for efficient lookups
            final var collection = database.getCollection(ELEMENTS_COLLECTION_NAME);
            collection.createIndex(
                    new Document().append("componentId", 1).append("componentVersionId", 1),
                    new IndexOptions().name("component_lookup"));
            collection.createIndex(
                    new Document().append("elementId", 1).append("elementType", 1),
                    new IndexOptions().name("element_lookup"));
        }
    }

    private static boolean collectionExists(final MongoDatabase database, final String collectionName) {
        for (final var name : database.listCollectionNames()) {
            if (name.equals(collectionName)) {
                return false;
            }
        }
        return true;
    }
}
