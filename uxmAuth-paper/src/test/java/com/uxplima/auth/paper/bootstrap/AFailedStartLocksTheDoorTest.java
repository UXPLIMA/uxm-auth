package com.uxplima.auth.paper.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * A start that fails leaves the door shut.
 *
 * <p>An offline server lets anybody in as anybody while no login plugin runs. A plugin that disabled itself over one
 * mistyped value in screens.conf turned that mistake into an open server, live on the Folia lab: a bot joined and
 * played with no password. The plugin stays enabled instead, says why in the console, and refuses every login.
 */
class AFailedStartLocksTheDoorTest {

    @AfterEach
    void unmock() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a value the plugin cannot read keeps it enabled and refuses every login with its reason")
    void aBadFileRefusesEveryLogin() throws Exception {
        ServerMock server = MockBukkit.mock();
        PluginEnablesTest.Loaded plugin = MockBukkit.load(PluginEnablesTest.Loaded.class);
        server.getPluginManager().disablePlugin(plugin);
        breakTheScreens(plugin.getDataFolder().toPath());

        server.getPluginManager().enablePlugin(plugin);

        assertThat(plugin.isEnabled())
                .as("a failed start must not disable the plugin")
                .isTrue();
        AsyncPlayerPreLoginEvent login = arriving();
        CompletableFuture.runAsync(() -> server.getPluginManager().callEvent(login))
                .get();
        assertThat(login.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
    }

    @SuppressWarnings("removal")
    private static AsyncPlayerPreLoginEvent arriving() {
        return new AsyncPlayerPreLoginEvent("Steve", InetAddress.getLoopbackAddress(), UUID.randomUUID());
    }

    private static void breakTheScreens(Path folder) throws IOException {
        Path screens = folder.resolve("screens.conf");
        String text = Files.readString(screens, StandardCharsets.UTF_8);
        Files.writeString(
                screens,
                text.replaceAll("(?m)^waiting-room\\s*=.*$", "waiting-room = \"nowhere\""),
                StandardCharsets.UTF_8);
    }
}
