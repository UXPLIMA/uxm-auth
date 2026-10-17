package com.uxplima.auth.paper.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

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
