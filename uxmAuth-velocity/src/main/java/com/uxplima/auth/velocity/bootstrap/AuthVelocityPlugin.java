package com.uxplima.auth.velocity.bootstrap;

import java.util.Objects;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.proxy.ProxyServer;

/**
 * The proxy half of a network. Velocity builds it through Guice and hands it the proxy.
 *
 * <p>There is no {@code @Plugin} annotation. That annotation only ever fed the annotation processor that
 * writes {@code velocity-plugin.json}, and this plugin writes that file by hand, so carrying both would mean
 * two places to change and one of them silently ignored. The proxy reads the file.
 *
 * <p>The proxy's work arrives in stage 4 of the plan: until then the module exists so that its fences and its
 * jar are proved from the first commit, and a network installs nothing here.
 */
public final class AuthVelocityPlugin {

    private final ProxyServer proxy;

    @Inject
    public AuthVelocityPlugin(ProxyServer proxy) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
    }

    /** Wiring waits for this event, because the command manager is only ready once the proxy has started. */
    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(proxy, "proxy");
    }
}
