package dev.spacepunk.prison.api;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiny service registry so modules can find each other without hard dependencies.
 * Register in your module's enable(); look up lazily (not in constructors).
 */
public final class Services {
    private static final Map<Class<?>, Object> REGISTRY = new ConcurrentHashMap<>();

    private Services() {}

    public static <T> void register(Class<T> type, T impl) {
        REGISTRY.put(type, Objects.requireNonNull(impl));
    }

    public static <T> T get(Class<T> type) {
        Object o = REGISTRY.get(type);
        if (o == null) throw new IllegalStateException("Service not registered: " + type.getSimpleName());
        return type.cast(o);
    }

    public static <T> java.util.Optional<T> find(Class<T> type) {
        return java.util.Optional.ofNullable(REGISTRY.get(type)).map(type::cast);
    }

    public static void clear() { REGISTRY.clear(); }
}
