package dev.iq.graph.persistence.mongodb;

import com.mongodb.client.MongoClients;
import dev.iq.common.persist.Session;
import dev.iq.common.persist.SessionFactory;

/**
 * MongoDB implementation of SessionFactory.
 */
public final class MongoSessionFactory implements SessionFactory {

    private final String connectionString;
    private final String databaseName;

    public MongoSessionFactory(final String connectionString, final String databaseName) {

        this.connectionString = connectionString;
        this.databaseName = databaseName;
    }

    @Override
    public Session create() {

        final var mongoClient = MongoClients.create(connectionString);
        return new MongoSession(mongoClient, databaseName);
    }
}