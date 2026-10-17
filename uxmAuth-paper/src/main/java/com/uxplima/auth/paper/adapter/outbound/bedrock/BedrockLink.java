package com.uxplima.auth.paper.adapter.outbound.bedrock;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.bukkit.Server;

/**
 * What Floodgate says about a connection: whether it vouches for it as a Bedrock player, and whether that
 * player is linked to a Java account. An optional link: without Floodgate nobody is a Bedrock player.
 */
public interface BedrockLink {

    /** A Bedrock player Floodgate vouched for. */
    record Player(boolean linked) {}

    /** The Java-only server. */
    BedrockLink NONE = new BedrockLink() {
        @Override
        public Optional<Player> player(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<String> prefix() {
            return Optional.empty();
        }
    };

    /** The characters a Java name is made of. A prefix of only these can make a Bedrock name a Java one. */
    Pattern JAVA_NAME = Pattern.compile("[A-Za-z0-9_]*");

    /** The Bedrock player behind this connection, or empty for a Java one. */
    Optional<Player> player(UUID id);

    /** The prefix Floodgate puts before every Bedrock name, while Floodgate runs. */
    Optional<String> prefix();

    /** The link for this server: Floodgate's when it runs, otherwise nobody is a Bedrock player. */
    static BedrockLink on(Server server, Logger logger) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(logger, "logger");
        org.bukkit.plugin.Plugin floodgate = server.getPluginManager().getPlugin("floodgate");
        if (floodgate == null || !floodgate.isEnabled()) {
            return NONE;
        }
        return new FloodgateLink(floodgate.getClass().getClassLoader(), logger);
    }

    /** Of the clients {@code clients} accepts, only the Java ones: a Bedrock client reads forms, not dialogs. */
    static java.util.function.Predicate<UUID> javaOnly(BedrockLink link, java.util.function.Predicate<UUID> clients) {
        Objects.requireNonNull(link, "link");
        Objects.requireNonNull(clients, "clients");
        return id -> link.player(id).isEmpty() && clients.test(id);
    }

    /** Whether this prefix lets a Bedrock name be read as a Java name: empty, or made of name characters. */
    static boolean collides(String prefix) {
        return JAVA_NAME.matcher(Objects.requireNonNull(prefix, "prefix")).matches();
    }
}
