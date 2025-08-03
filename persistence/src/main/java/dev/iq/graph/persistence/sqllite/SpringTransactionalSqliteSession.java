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
 * Spring transaction-aware SQLite session implementation.
 */
public final class SpringTransactionalSqliteSession implements Session, SqliteHandleProvider {

    private final Jdbi jdbi;

    public SpringTransactionalSqliteSession(final Jdbi jdbi) {

        this.jdbi = jdbi;
    }

    private Handle fallbackHandle = null;

    @Override
    public Handle handle() {

        // Always get handle from Spring transaction context
        final var transactionHandle = SqliteTransactionHelper.getCurrentHandle();
        if (transactionHandle != null) {
            return transactionHandle;
        }
        // For non-transactional operations, use a single shared handle
        if ((fallbackHandle == null) || !isHandleValid()) {
            fallbackHandle = jdbi.open();
        }
        return fallbackHandle;
    }

    @Override
    public void commit() {

        // No-op - handled by Spring transaction manager
    }

    @Override
    public void rollback() {

        // No-op - handled by Spring transaction manager
    }

    @Override
    public void close() {

        // No-op - handle lifecycle managed by Spring
        // Clean up fallback handle if it exists
        if (fallbackHandle != null) {
            Io.withVoid(fallbackHandle::close);
            fallbackHandle = null;
        }
    }

    private boolean isHandleValid() {

        return Io.withReturn(
                () -> (fallbackHandle != null) && fallbackHandle.getConnection().isValid(1));
    }
}
