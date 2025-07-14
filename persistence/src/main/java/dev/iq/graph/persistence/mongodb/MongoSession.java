package dev.iq.graph.persistence.mongodb;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCluster;
import com.mongodb.client.MongoDatabase;
import dev.iq.common.persist.Session;

/**
 * MongoDB implementation of Session with transaction support.
 */
public final class MongoSession implements Session {

    private final ClientSession clientSession;
    private final MongoDatabase database;

    public MongoSession(final MongoCluster mongoClient, final String databaseName) {

        clientSession = mongoClient.startSession();
        clientSession.startTransaction();
        database = mongoClient.getDatabase(databaseName);
    }

    @Override
    public void commit() {

        clientSession.commitTransaction();
    }

    @Override
    public void rollback() {

        clientSession.abortTransaction();
    }

    @Override
    public void close() {

        clientSession.close();
    }

    MongoDatabase database() {

        return database;
    }
}
