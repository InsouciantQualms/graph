/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

/**
 * Base interface for graph and component spaces.
 * Provides access to a view for querying graph elements.
 */
public interface Space {

    /** Returns a view for querying elements in this space. */
    View view();
}
