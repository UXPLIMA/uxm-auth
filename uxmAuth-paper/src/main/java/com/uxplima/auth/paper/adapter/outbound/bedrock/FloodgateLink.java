package com.uxplima.auth.paper.adapter.outbound.bedrock;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Floodgate's answers, reached through its own class loader by name: {@code FloodgateApi.getInstance()},
 * {@code getPlayer(uuid).isLinked()} and {@code getPlayerPrefix()}. No Floodgate class is named in this plugin,
 * so a server without it loads none, and anything that goes wrong reads as a Java player.
 */
final class FloodgateLink implements BedrockLink {

    private static final String API = "org.geysermc.floodgate.api.FloodgateApi";

    private final ClassLoader loader;
    private final Logger logger;

    FloodgateLink(ClassLoader loader, Logger logger) {
        this.loader = loader;
        this.logger = logger;
    }

    @Override
    public Optional<BedrockLink.Player> player(UUID id) {
        try {
            Object api = api();
            Object player = api.getClass().getMethod("getPlayer", UUID.class).invoke(api, id);
            if (player == null) {
                return Optional.empty();
            }
            Object linked = player.getClass().getMethod("isLinked").invoke(player);
            return Optional.of(new BedrockLink.Player(Boolean.TRUE.equals(linked)));
        } catch (ReflectiveOperationException | RuntimeException failed) {
            logger.log(Level.FINE, "Floodgate could not tell whether a player is a Bedrock one", failed);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> prefix() {
        try {
            Object api = api();
            return Optional.ofNullable(
                    (String) api.getClass().getMethod("getPlayerPrefix").invoke(api));
        } catch (ReflectiveOperationException | RuntimeException failed) {
            logger.log(Level.FINE, "Floodgate could not tell its name prefix", failed);
            return Optional.empty();
        }
    }

    private Object api() throws ReflectiveOperationException {
        return Class.forName(API, true, loader).getMethod("getInstance").invoke(null);
    }
}
