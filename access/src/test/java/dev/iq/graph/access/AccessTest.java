package dev.iq.graph.access;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccessTest {
    @Test
    void testGreet() {
        Access app = new Access();
        assertEquals("Hello, Test!", app.greet("Test"));
    }
}