package com.uxplima.auth.paper.adapter.inbound.screen;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** A client with no player yet holds only what the server grants to everyone. */
final class PermissionDefaultsTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a permission granted to everyone is held, one for operators or unknown is not")
    void onlyWhatEveryoneHolds() {
        server.getPluginManager().addPermission(new Permission("uxmauth.login", PermissionDefault.TRUE));
        server.getPluginManager().addPermission(new Permission("uxmauth.register", PermissionDefault.OP));
        PermissionDefaults defaults = new PermissionDefaults(server.getPluginManager());

        assertThat(defaults.test("uxmauth.login")).isTrue();
        assertThat(defaults.test("uxmauth.register")).isFalse();
        assertThat(defaults.test("uxmauth.nothing")).isFalse();
    }
}
