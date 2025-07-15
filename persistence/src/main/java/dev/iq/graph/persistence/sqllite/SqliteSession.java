/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */


package dev.iq.graph.persistence.sqllite;

import dev.iq.common.fp.Io;
import dev.iq.common.persist.Session;

import java.sql.Connection;

/**
 * SQLite implementation of Session with transaction support.
 */
public final class SqliteSession implements Session {

    private final Connection connection;

    public SqliteSession(final Connection connection) {

        this.connection = connection;
        Io.withVoid(() -> connection.setAutoCommit(false));
    }

    @Override
    public void commit() {

        Io.withVoid(connection::commit);
    }

    @Override
    public void rollback() {

        Io.withVoid(connection::rollback);
    }

    @Override
    public void close() {

        Io.withVoid(connection::close);
    }

    Connection connection() {

        return connection;
    }
}