package com.uxplima.auth.application.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.InactiveAccounts;
import org.jspecify.annotations.Nullable;

/**
 * Removes the accounts nobody logged in to for the operator's window, with everything kept under them, in batches.
 * On a network every server writes its logins to the one table, so a login anywhere keeps an account. It is off
 * until the operator turns it on. It ticks often and runs when its check is due, so a reload of the settings counts.
 */
public final class PurgeInactive {

    /**
     * The operator's purge.
     *
     * @param after how long without a login an account is kept
     * @param every how often the purge looks
     */
    public record Settings(boolean enabled, Duration after, boolean keepPremium, Duration every) {
        public Settings {
            Objects.requireNonNull(after, "after");
            Objects.requireNonNull(every, "every");
        }
    }

    /** How many accounts a run removed. */
    public record Purged(int removed) {}

    private static final int BATCH = 500;

    private final InactiveAccounts inactive;
    private final AccountRepository accounts;
    private final Supplier<Settings> settings;
    private final Clock clock;
    private final AtomicReference<@Nullable Instant> lastRun = new AtomicReference<>();

    public PurgeInactive(
            InactiveAccounts inactive, AccountRepository accounts, Supplier<Settings> settings, Clock clock) {
        this.inactive = Objects.requireNonNull(inactive, "inactive");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Runs the purge when it is on and due; empty when it did not run. It writes the table: off any server thread. */
    public Optional<Purged> tick() {
        Settings now = settings.get();
        Instant at = clock.instant();
        Instant last = lastRun.get();
        if (!now.enabled() || (last != null && at.isBefore(last.plus(now.every())))) {
            return Optional.empty();
        }
        lastRun.set(at);
        Instant before = at.minus(now.after());
        int removed = 0;
        List<UUID> batch;
        do {
            batch = inactive.inactiveSince(before, now.keepPremium(), BATCH);
            batch.forEach(accounts::delete);
            removed += batch.size();
        } while (batch.size() == BATCH);
        return Optional.of(new Purged(removed));
    }
}
