package com.uxplima.auth.handshake;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import io.papermc.paper.configuration.GlobalConfiguration;
import io.papermc.paper.network.ChannelInitializeListenerHolder;

import net.kyori.adventure.key.Key;

import net.minecraft.network.HandlerNames;
import net.minecraft.server.MinecraftServer;
import org.spigotmc.SpigotConfig;

/**
 * Puts the hello interceptor on every new connection, where this server can run the handshake at all.
 *
 * <p>It cannot in online mode, where the server proves every player itself, and a second handshake would
 * disconnect them, nor behind a proxy, which is where a network's handshake belongs. In either case nothing is
 * installed and the reason goes to the console.
 */
public final class Handshake {

    private static final Key LISTENER = Key.key("uxmauth", "hello");
    private static final String HANDLER = "uxmauth_hello";

    private Handshake() {}

    /** Why this server cannot run the handshake, or empty when it can. */
    public static Optional<String> whyNot() {
        if (MinecraftServer.getServer().usesAuthentication()) {
            return Optional.of("online-mode is on in server.properties, so the server proves every player itself."
                    + " Premium auto-login stays off; turn online-mode off to let uxmAuth ask paid accounts alone.");
        }
        GlobalConfiguration.Proxies proxies = GlobalConfiguration.get().proxies;
        if (proxies.velocity.enabled || SpigotConfig.bungee) {
            return Optional.of("this server sits behind a proxy, where the handshake belongs."
                    + " Premium auto-login stays off here; install uxmAuth on the proxy.");
        }
        if (ServerLogin.CHALLENGE == null) {
            return Optional.of("this server's login is not the one this uxmAuth was built against."
                    + " Premium auto-login stays off until uxmAuth is updated for it.");
        }
        return Optional.empty();
    }

    /** Puts the interceptor on every connection made from now on. */
    public static void install(HelloDoor door, HandshakeResults results, Logger logger) {
        Objects.requireNonNull(door, "door");
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(logger, "logger");
        ChannelInitializeListenerHolder.addListener(
                LISTENER,
                channel -> channel.pipeline()
                        .addBefore(
                                HandlerNames.PACKET_HANDLER,
                                HANDLER,
                                new HelloInterceptor(door, results, ServerLogin::of, logger)));
    }

    /** Takes the interceptor off connections made from now on. */
    public static void uninstall() {
        ChannelInitializeListenerHolder.removeListener(LISTENER);
    }
}
