package com.uxplima.auth.paper.adapter.inbound.factor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Each hold lets in only the players it holds: one duty done never opens the hold of another. */
final class OwedSetupsTest {

    @TempDir
    Path folder;

    private AuthWorld world;
    private PlayerMock steve;

    @BeforeEach
    void setUp() {
        var server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("hunter22"), "127.0.0.1")
                        .join())
                .isInstanceOf(LoginResult.LoggedIn.class);
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private AuthState.Authenticated in() {
        return new AuthState.Authenticated(
                world.accounts.findByName(new PlayerName("Steve")).orElseThrow(), AuthMethod.PASSWORD);
    }

    @Test
    @DisplayName("a release by a hold that does not hold the player changes nothing, and the right one lets them in")
    void anotherHoldDoesNotRelease() {
        OwedSetups forTheApp = new OwedSetups(world.sessions);
        OwedSetups forTheAddress = new OwedSetups(world.sessions);
        AtomicInteger proceeded = new AtomicInteger();
        forTheAddress.hold(steve, in(), proceeded::incrementAndGet);

        assertThat(forTheApp.release(steve)).isFalse();
        assertThat(world.sessions.state(steve.getUniqueId())).containsInstanceOf(AuthState.SettingUp.class);
        assertThat(proceeded).hasValue(0);

        assertThat(forTheAddress.release(steve)).isTrue();
        assertThat(world.sessions.state(steve.getUniqueId())).contains(in());
        assertThat(proceeded).hasValue(1);
    }

    @Test
    @DisplayName("a player who left is forgotten: a release finds nothing to let in")
    void aPlayerWhoLeftIsForgotten() {
        OwedSetups owed = new OwedSetups(world.sessions);
        AtomicInteger proceeded = new AtomicInteger();
        owed.hold(steve, in(), proceeded::incrementAndGet);

        owed.forget(steve.getUniqueId());

        assertThat(owed.release(steve)).isFalse();
        assertThat(proceeded).hasValue(0);
    }
}
