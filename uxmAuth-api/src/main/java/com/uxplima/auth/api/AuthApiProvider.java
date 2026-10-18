package com.uxplima.auth.api;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

/**
 * Where another plugin finds the API, on Paper and on Velocity alike. Empty while uxmAuth is not enabled, so a
 * plugin that only might run beside it asks and goes on without it.
 */
public final class AuthApiProvider {

    private static final AtomicReference<@Nullable AuthApi> CURRENT = new AtomicReference<>();

    private AuthApiProvider() {}

    /** The API, while uxmAuth is enabled. */
    public static Optional<AuthApi> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * uxmAuth hands its API over as it enables. Nothing else calls this.
     *
     * @return what takes it back as uxmAuth disables, and leaves a later one in place
     */
    public static Runnable offer(AuthApi api) {
        Objects.requireNonNull(api, "api");
        CURRENT.set(api);
        return () -> CURRENT.compareAndSet(api, null);
    }
}
