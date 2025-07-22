/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import org.jdbi.v3.core.Handle;

/**
 * Interface for providing JDBI Handle instances.
 */
public interface SqliteHandleProvider {

    /**
     * Gets a JDBI Handle for database operations.
     */
    Handle handle();
}
