package com.uxplima.auth.paper.adapter.inbound.hold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.AuthSettings.Moment;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A bar put up while a player waits comes down when the wait is over, and one put up after it stays. */
final class WaitingBarsTest {

    private final Sessions sessions = new Sessions();
    private final WaitingBars bars = new WaitingBars(sessions);
    private final List<Moment> fired = new ArrayList<>();
    private final MomentRunner runner = WaitingBars.endWithTheWait((player, moment) -> fired.add(moment), bars);

    private static BossBar bar() {
        return BossBar.bossBar(Component.text("wait"), 1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    }

    private Player waiting(String name) {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        sessions.open(id, new AuthState.Unregistered(new PlayerName(name)));
        return player;
    }

    private void logIn(Player player) {
        Account account = new Account(
                player.getUniqueId(),
                new PlayerName("Steve"),
                AccountKind.CRACKED,
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                "",
                null,
                null);
        sessions.update(player.getUniqueId(), state -> new AuthState.Authenticated(account, AuthMethod.PASSWORD));
    }

    @Test
    @DisplayName("a bar shown while the player waits comes down at the login, before the login's own list runs")
    void theWaitingBarComesDownAtTheLogin() {
        Player steve = waiting("Steve");
        BossBar waitingBar = bar();
        bars.shown(steve, waitingBar);

        logIn(steve);
        runner.fire(steve, Moment.LOGGED_IN);

        verify(steve).hideBossBar(waitingBar);
        assertThat(fired).containsExactly(Moment.LOGGED_IN);
    }

    @Test
    @DisplayName("a registration ends the wait too, and a reminder does not")
    void aRegistrationEndsItAndAReminderDoesNot() {
        Player steve = waiting("Steve");
        BossBar waitingBar = bar();
        bars.shown(steve, waitingBar);

        runner.fire(steve, Moment.REMINDER_LOGIN);
        verify(steve, never()).hideBossBar(waitingBar);

        logIn(steve);
        runner.fire(steve, Moment.REGISTERED);
        verify(steve).hideBossBar(waitingBar);
    }

    @Test
    @DisplayName("a bar shown after the login is the operator's to keep, and nothing takes it down early")
    void aBarAfterTheLoginStays() {
        Player steve = waiting("Steve");
        logIn(steve);
        BossBar welcome = bar();
        bars.shown(steve, welcome);

        runner.fire(steve, Moment.LOGGED_IN);

        verify(steve, never()).hideBossBar(welcome);
    }

    @Test
    @DisplayName("a player who left is let go at the next bar, so nothing is kept for them")
    void aPlayerWhoLeftIsLetGo() {
        Player steve = waiting("Steve");
        bars.shown(steve, bar());
        sessions.close(steve.getUniqueId());

        bars.shown(waiting("Alex"), bar());

        assertThat(bars.waiting()).isEqualTo(1);
    }
}
