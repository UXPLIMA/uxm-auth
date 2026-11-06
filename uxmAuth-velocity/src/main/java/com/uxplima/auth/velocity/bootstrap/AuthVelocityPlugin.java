package com.uxplima.auth.velocity.bootstrap;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.google.inject.Inject;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.velocity.adapter.inbound.LockedProxyDoor;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jspecify.annotations.Nullable;
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
        ProxyModule module = new ProxyModule(this, proxy, logger, dataDirectory);
        if (!started(module::wire, logger)) {
            lock(module);
        }
    }

    /**
     * Whether the start went through. A start the module refused, over a changed UUID scheme or a waiting world it
     * cannot build, has said why. An Error counts as much as an exception: a class that does not load or link leaves
     * no login running either, and an Error the proxy caught would leave the network open with no door at all.
     */
    static boolean started(BooleanSupplier wire, Logger logger) {
        try {
            return wire.getAsBoolean();
        } catch (Throwable failed) {
            logger.error("uxmAuth could not start: {}", failed.getMessage(), failed);
            return false;
        }
    }

    /**
     * A start that failed shuts the door rather than leaving the network open to anybody under any name. What the
     * failed start registered answers out of a graph that is not whole, so it goes first. Every step is guarded on
     * its own, so one that fails never skips the door.
     */
    private void lock(ProxyModule module) {
        try {
            module.shutdown();
        } catch (Throwable halfBuilt) {
            logger.warn("What the failed start had opened did not all close", halfBuilt);
        }
        try {
            proxy.getEventManager().unregisterListeners(this);
        } catch (Throwable stuck) {
            logger.warn("The listeners of the failed start could not all be removed", stuck);
        }
        proxy.getEventManager().register(this, new LockedProxyDoor(reason(module.messages())));
        logger.error("Every login is refused until uxmAuth starts. Fix the cause above, then restart.");
    }

    /** The words of the operator's language when the start read them; the key's own English otherwise. */
    static Component reason(@Nullable Messages words) {
        if (words != null) {
            try {
                return words.render(Audience.empty(), Keys.START_FAILED_KICK);
            } catch (Throwable unreadable) {
                // The catalogue may be what failed: the English below still shuts the door.
            }
        }
        return LockedProxyDoor.defaultReason();
    }
}
