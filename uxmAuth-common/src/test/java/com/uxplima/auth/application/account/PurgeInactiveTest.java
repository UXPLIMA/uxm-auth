package com.uxplima.auth.application.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.testing.InMemoryAccounts;
import com.uxplima.auth.testing.TurnedClock;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Accounts nobody logged in to for the operator's window go, when the operator turns the purge on. */
final class PurgeInactiveTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final TurnedClock clock = new TurnedClock(NOW);
    private PurgeInactive.Settings settings =
            new PurgeInactive.Settings(true, Duration.ofDays(180), true, Duration.ofHours(24));
    private final PurgeInactive purge = new PurgeInactive(accounts, accounts, () -> settings, clock);

    private int counter;

    /** An account registered and last logged in this many days ago; no login when {@code loginDays} is null. */
    private UUID account(AccountKind kind, int registeredDays, @Nullable Integer loginDays) {
        Account account = new Account(
                UUID.randomUUID(),
                new PlayerName("Player" + counter++),
                kind,
                kind == AccountKind.PREMIUM ? UUID.randomUUID() : null,
                null,
                NOW.minus(Duration.ofDays(registeredDays)),
                "",
                loginDays == null ? null : NOW.minus(Duration.ofDays(loginDays)),
                null);
        accounts.seed(account);
        return account.id();
    }

    private boolean kept(UUID id) {
        return accounts.findById(id).isPresent();
    }

    @Test
    @DisplayName("an old login goes, a recent one stays, and an account never logged in counts from its registration")
    void oldGoesRecentStays() {
        UUID old = account(AccountKind.CRACKED, 400, 200);
        UUID recent = account(AccountKind.CRACKED, 400, 10);
        UUID neverOld = account(AccountKind.CRACKED, 200, null);
        UUID neverNew = account(AccountKind.CRACKED, 10, null);

        assertThat(purge.tick()).contains(new PurgeInactive.Purged(2));

        assertThat(kept(old)).isFalse();
        assertThat(kept(neverOld)).isFalse();
        assertThat(kept(recent)).isTrue();
        assertThat(kept(neverNew)).isTrue();
    }

    @Test
    @DisplayName("a premium account stays while kept, and goes like any other when not")
    void premiumStaysWhenKept() {
        UUID paid = account(AccountKind.PREMIUM, 400, 200);
        purge.tick();
        assertThat(kept(paid)).isTrue();

        settings = new PurgeInactive.Settings(true, Duration.ofDays(180), false, Duration.ofHours(24));
        clock.turn(Duration.ofHours(24));
        purge.tick();
        assertThat(kept(paid)).isFalse();
    }

    @Test
    @DisplayName("a login on another server of the network inside the window keeps the account")
    void aLoginElsewhereKeepsIt() {
        UUID roamer = account(AccountKind.CRACKED, 400, 200);
        // Every server writes the shared table, so the login on the lobby is the one this server reads.
        accounts.recordLogin(roamer, NOW.minus(Duration.ofDays(3)), "10.0.0.9");

        assertThat(purge.tick()).contains(new PurgeInactive.Purged(0));
        assertThat(kept(roamer)).isTrue();
    }

    @Test
    @DisplayName("off removes nothing, and a tick before the next check is due runs nothing")
    void offAndNotDueRemoveNothing() {
        UUID old = account(AccountKind.CRACKED, 400, 200);
        settings = new PurgeInactive.Settings(false, Duration.ofDays(180), true, Duration.ofHours(24));
        assertThat(purge.tick()).isEmpty();
        assertThat(kept(old)).isTrue();

        settings = new PurgeInactive.Settings(true, Duration.ofDays(180), true, Duration.ofHours(24));
        purge.tick();
        UUID later = account(AccountKind.CRACKED, 400, 200);
        clock.turn(Duration.ofHours(23));
        assertThat(purge.tick()).isEmpty();
        assertThat(kept(later)).isTrue();
        clock.turn(Duration.ofHours(1));
        assertThat(purge.tick()).contains(new PurgeInactive.Purged(1));
    }

    @Test
    @DisplayName("more accounts than one batch all go in one run")
    void batches() {
        for (int i = 0; i < 1203; i++) {
            account(AccountKind.CRACKED, 400, 200);
        }

        assertThat(purge.tick()).contains(new PurgeInactive.Purged(1203));
        assertThat(accounts.all()).isEmpty();
    }
}
