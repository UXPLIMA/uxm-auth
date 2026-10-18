package com.uxplima.auth.paper.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import com.uxplima.auth.adapter.outbound.RecordedScheme;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.exception.UnimplementedOperationException;

/**
 * The whole plugin, enabled the way a server enables it, on a server with nothing else on it.
 *
 * <p>Every other test builds one part and hands it what it needs. On 2026-09-23 uxm-plots had about two thousand
 * green tests and still could not enable: a part read something built later in the wiring, and two windows
 * registered one placeholder. Only enabling the whole graph shows that, so this enables it.
 */
class PluginEnablesTest {

    private ServerMock server;

    private Loaded plugin;

    @BeforeEach
    void enable() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Loaded.class);
    }

    @AfterEach
    void disable() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the plugin enables with nothing in its folder but what it writes itself")
    void itEnables() {
        assertThat(plugin.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("the plugin disables cleanly, and a second enable works")
    void itDisablesAndEnablesAgain() {
        server.getPluginManager().disablePlugin(plugin);
        assertThat(plugin.isEnabled()).isFalse();

        MockBukkit.unmock();
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Loaded.class);

        assertThat(plugin.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("the first start writes the mails, one file per kind and language, where an operator edits them")
    void theFirstStartWritesTheMails() {
        Path mail = plugin.getDataFolder().toPath().resolve("mail");

        assertThat(mail.resolve("login-code_en.txt")).isRegularFile();
        assertThat(mail.resolve("recovery_tr.txt")).isRegularFile();
    }

    @Test
    @DisplayName("the first start writes its UUID scheme into the database, for every later start to be held to")
    void theFirstStartRecordsTheUuidScheme() {
        Path file = plugin.getDataFolder().toPath().resolve("auth.db");
        try (Database database =
                Database.builder().sqlite(file).poolName("uxmauth-reader").build()) {
            assertThat(new RecordedScheme(database).recorded()).contains(UuidScheme.KEEP_FIRST);
        }
    }

    /**
     * The plugin, with one gap of the mock server tolerated: it does not implement {@code cancel()} on a scheduled
     * task, and a shutdown cancels its tasks. A real server stops them cleanly.
     */
    public static class Loaded extends AuthPlugin {

        @Override
        public void onDisable() {
            try {
                super.onDisable();
            } catch (UnimplementedOperationException mockCannotCancelATask) {
                // The comment on this class says why.
            }
        }
    }
}
