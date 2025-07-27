package dev.iq.graph.access;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class AccessTest {

    @Test
    void testGreet() {
        final var app = new Access();
        assertEquals("Hello, Test!", app.greet("Test"));
    }
}
