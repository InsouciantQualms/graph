/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import dev.iq.common.fp.Io;
import dev.iq.graph.persistence.Session;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

/**
 * SQLite implementation of Session with transaction support.
 */
public final class SqliteSession implements Session, SqliteHandleProvider {

    private final Handle handle;

    public SqliteSession(final Jdbi jdbi) {

        handle = jdbi.open();

        // Enable foreign keys for SQLite (must be done per connection)
        Io.withVoid(() -> handle.execute("PRAGMA foreign_keys = ON"));

        handle.begin();
    }

    @Override
    public void commit() {

        Io.withVoid(handle::commit);
    }

    @Override
    public void rollback() {

        Io.withVoid(handle::rollback);
    }

    @Override
    public void close() {

        Io.withVoid(() -> {
            // JDBI requires transactions to be explicitly committed or rolled back
            // before closing. If transaction is still open, roll it back.
            if (handle.isInTransaction()) {
                handle.rollback();
            }
            handle.close();
        });
    }

    @Override
    public Handle handle() {

        return handle;
    }
}
