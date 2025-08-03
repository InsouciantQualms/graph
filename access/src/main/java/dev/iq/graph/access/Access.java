package dev.iq.graph.access;

import dev.iq.common.log.Log;

final class Access {

    public String greet(final String name) {
        return "Hello, " + name + '!';
    }

    public static void main(final String... args) {
        final var app = new Access();
        Log.info(Access.class, () -> app.greet("World"));
    }
}
