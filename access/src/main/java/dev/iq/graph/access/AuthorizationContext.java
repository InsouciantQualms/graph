package dev.iq.graph.access;

import dev.iq.common.version.Locator;

public interface AuthorizationContext {

    String principal();

    Locator target();

    Operation operation();
}
