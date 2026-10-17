package com.uxplima.auth.paper.adapter.inbound.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Behind a proxy the door tells it each login it lets in and each player it holds, so every server agrees. */
final class DoorReportsTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private PlayerMock loggedIn(AuthMethod method) {
        PlayerMock player = server.addPlayer("Steve");
        Account account = new Account(
                player.getUniqueId(),
                new PlayerName("Steve"),
                AccountKind.CRACKED,
                null,
                null,
                world.clock.instant(),
                "127.0.0.1",
                null,
                null);
        world.sessions.close(player.getUniqueId());
        world.sessions.open(player.getUniqueId(), new AuthState.Authenticated(account, method));
        return player;
    }

    @Test
    @DisplayName("a login the door lets in is reported with the way the player came in")
    void aLoginIsReported() {
        PlayerMock steve = loggedIn(AuthMethod.PASSWORD);

        world.door.open(steve, AuthSettings.Moment.LOGGED_IN);

        assertThat(world.reported).containsExactly("in:PASSWORD");
    }

    @Test
    @DisplayName(
            "a player who arrives logged in by Floodgate or at the dialog is reported, one the network vouched for is not")
    void anArrivalLoggedInIsReportedUnlessTheNetworkSentIt() {
        world.door.hold(loggedIn(AuthMethod.BEDROCK));
        assertThat(world.reported).containsExactly("in:BEDROCK");

        world.reported.clear();
        world.door.hold(loggedIn(AuthMethod.NETWORK));
        assertThat(world.reported).isEmpty();
    }

    @Test
    @DisplayName("a player held to wait for a password, after a logout or on arrival, is reported waiting")
    void aHeldPlayerIsReportedWaiting() {
        PlayerMock steve = server.addPlayer("Alex");
        world.sessions.close(steve.getUniqueId());
        world.sessions.open(steve.getUniqueId(), new AuthState.Unregistered(new PlayerName("Alex")));

        world.door.hold(steve);

        assertThat(world.reported).containsExactly("waiting");
    }

    @Test
    @DisplayName("a player the door has no session for is reported nothing")
    void nobodyIsReportedNothing() {
        PlayerMock ghost = server.addPlayer("Ghost");
        world.sessions.close(ghost.getUniqueId());

        world.door.hold(ghost);

        assertThat(world.reported).isEmpty();
    }
}
