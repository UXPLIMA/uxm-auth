package com.uxplima.auth.paper.adapter.inbound.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.auth.adapter.outbound.AuthSettings.Moment;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** A login runs the list for every login, then the one for the way the player came in. */
final class MomentsByMethodTest {

    private ServerMock server;
    private final Sessions sessions = new Sessions();
    private final List<Moment> fired = new ArrayList<>();
    private final MomentRunner runner = MomentsByMethod.of((player, moment) -> fired.add(moment), sessions);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock inBy(AuthMethod method) {
        PlayerMock player = server.addPlayer();
        Account account = new Account(
                UUID.randomUUID(),
                new PlayerName(player.getName()),
                AccountKind.CRACKED,
                null,
                null,
                Instant.EPOCH,
                "",
                null,
                null);
        sessions.open(player.getUniqueId(), new AuthState.Authenticated(account, method));
        return player;
    }

    @Test
    @DisplayName("each way in has its own list after the shared one, and a resumed session is one of them")
    void eachMethodHasItsList() {
        runner.fire(inBy(AuthMethod.PASSWORD), Moment.LOGGED_IN);
        runner.fire(inBy(AuthMethod.PREMIUM), Moment.LOGGED_IN);
        runner.fire(inBy(AuthMethod.BEDROCK), Moment.LOGGED_IN);
        runner.fire(inBy(AuthMethod.SESSION), Moment.LOGGED_IN);
        runner.fire(inBy(AuthMethod.FORCED), Moment.LOGGED_IN);

        assertThat(fired)
                .containsExactly(
                        Moment.LOGGED_IN,
                        Moment.LOGGED_IN_PASSWORD,
                        Moment.LOGGED_IN,
                        Moment.LOGGED_IN_PREMIUM,
                        Moment.LOGGED_IN,
                        Moment.LOGGED_IN_BEDROCK,
                        Moment.LOGGED_IN,
                        Moment.LOGGED_IN_SESSION,
                        Moment.LOGGED_IN,
                        Moment.LOGGED_IN_FORCED);
    }

    @Test
    @DisplayName("every other moment, a login the network vouched for, and a player with no session run as they are")
    void othersPassThrough() {
        runner.fire(inBy(AuthMethod.NETWORK), Moment.LOGGED_IN);
        runner.fire(inBy(AuthMethod.PASSWORD), Moment.WRONG_PASSWORD);
        runner.fire(server.addPlayer(), Moment.LOGGED_IN);

        assertThat(fired).containsExactly(Moment.LOGGED_IN, Moment.WRONG_PASSWORD, Moment.LOGGED_IN);
    }
}
