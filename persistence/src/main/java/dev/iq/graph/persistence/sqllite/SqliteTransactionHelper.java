/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import org.jdbi.v3.core.Handle;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Helper class for SQLite transaction management.
 */
public final class SqliteTransactionHelper {

    private static final String HANDLE_KEY = "sqlite.jdbi.handle";

    private SqliteTransactionHelper() {
        // Utility class
    }

    /**
     * Gets the current transaction handle from thread-local storage.
     */
    public static Handle getCurrentHandle() {

        return (Handle) TransactionSynchronizationManager.getResource(HANDLE_KEY);
    }

    /**
     * Checks if there is an active transaction handle.
     */
    public static boolean hasActiveHandle() {

        return getCurrentHandle() != null;
    }
}
