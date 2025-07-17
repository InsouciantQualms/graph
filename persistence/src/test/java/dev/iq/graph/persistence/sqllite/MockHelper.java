/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.jdbi.v3.core.result.ResultIterable;

/**
 * Helper utility for creating type-safe mocks in tests.
 */
final class MockHelper {

    private MockHelper() {
        // Utility class
    }

    /**
     * Creates a mock ResultIterable with proper type parameter.
     */
    @SuppressWarnings("unchecked")
    static <T> ResultIterable<T> mockResultIterable() {
        return (ResultIterable<T>) mock(ResultIterable.class);
    }

    /**
     * Creates an array of Optional values for varargs usage.
     */
    @SuppressWarnings("unchecked")
    static <T> Optional<T>[] optionals(Optional<T>... options) {
        return options;
    }

    /**
     * Sets up stubbing for methods that return different values on consecutive calls.
     */
    @SuppressWarnings("unchecked")
    static <T> void thenReturnConsecutive(
            org.mockito.stubbing.OngoingStubbing<Optional<T>> stubbing, Optional<T> first, Optional<T> second) {
        stubbing.thenReturn(first, second);
    }
}
