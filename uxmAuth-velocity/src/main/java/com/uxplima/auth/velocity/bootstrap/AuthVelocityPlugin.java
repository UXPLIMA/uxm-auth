package com.uxplima.auth.velocity.bootstrap;

import java.nio.file.Path;
import java.util.Objects;

import com.google.inject.Inject;
import com.uxplima.auth.velocity.adapter.inbound.LockedProxyDoor;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

/**
 * The proxy half of a network. Velocity builds it through Guice and hands it the proxy, a logger and the folder
 * it may write in. It owns the lifecycle and nothing else; the graph is built in {@link ProxyModule}.
 *
 * <p>There is no {@code @Plugin} annotation. That annotation only ever fed the annotation processor that
 * writes {@code velocity-plugin.json}, and this plugin writes that file by hand, so carrying both would mean
 * two places to change and one of them silently ignored. The proxy reads the file.
 */
public final class AuthVelocityPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    @Inject
    public AuthVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
    }

    /** Wiring waits for this event, because the command manager is only ready once the proxy has started. */
    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        Objects.requireNonNull(event, "event");
        boolean started;
        try {
            // A start the module refused, over a changed UUID scheme or a waiting world it cannot build, has said why.
            started = new ProxyModule(this, proxy, logger, dataDirectory).wire();
        } catch (RuntimeException failed) {
            logger.error("uxmAuth could not start: {}", failed.getMessage(), failed);
            started = false;
        }
        if (!started) {
            // A start that failed shuts the door rather than leaving the network open to anybody under any name.
            proxy.getEventManager().register(this, new LockedProxyDoor());
            logger.error("Every login is refused until uxmAuth starts. Fix the cause above, then restart.");
        }
    }
}
