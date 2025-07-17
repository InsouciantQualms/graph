/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

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
