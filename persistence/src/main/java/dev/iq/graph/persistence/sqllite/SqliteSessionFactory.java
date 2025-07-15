/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */


package dev.iq.graph.persistence.sqllite;

import dev.iq.common.fp.Io;
import dev.iq.common.persist.Session;
import dev.iq.common.persist.SessionFactory;

import javax.sql.DataSource;

/**
 * SQLite implementation of SessionFactory.
 */
public final class SqliteSessionFactory implements SessionFactory {

    private final DataSource dataSource;

    public SqliteSessionFactory(final DataSource dataSource) {

        this.dataSource = dataSource;
    }

    @Override
    public Session create() {

        final var connection = Io.withReturn(dataSource::getConnection);
        return new SqliteSession(connection);
    }
}
