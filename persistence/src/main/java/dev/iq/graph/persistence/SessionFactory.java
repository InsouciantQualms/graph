/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence;

/**
 * Factory for creating Session instances.
 */
public interface SessionFactory {

    /**
     * Creates a new Session instance.
     */
    Session create();
}
