package com.uxplima.auth.application.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.factor.FactorCheck;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.application.staff.Unregister;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.AuthState;
import org.jspecify.annotations.Nullable;

/**
 * A player removes their own account. They ask first, and then prove it is theirs inside a window: by the app's
 * code when the account has the app, by the password otherwise. A wrong proof closes the window, so every guess
 * costs an ask.
 */
public final class DeleteOwnAccount {

    /** What an ask or a confirmation came to. */
    public sealed interface Deleted {
        /** The window is open for this long; the proof is the app's code when {@code byCode}. */
        record Asked(Duration window, boolean byCode) implements Deleted {}

        /** The account is gone, and every member has heard. */
        record Removed() implements Deleted {}

        /** The proof was wrong, and the window is closed. */
        record WrongProof() implements Deleted {}

        /** Nothing was asked, or the window had closed. */
        record NotAsked() implements Deleted {}

        /** The player is not logged in. */
        record NotLoggedIn() implements Deleted {}

        /** The account has no password and no app to prove it by. */
        record NoProof() implements Deleted {}
    }

    /** The collaborators that read and remove the account. */
    public record Tables(Sessions sessions, AccountRepository accounts, Factors factors, Unregister unregister) {
        public Tables {
            Objects.requireNonNull(sessions, "sessions");
            Objects.requireNonNull(accounts, "accounts");
            Objects.requireNonNull(factors, "factors");
            Objects.requireNonNull(unregister, "unregister");
        }
    }

    private final Tables tables;
    private final FactorCheck codes;
    private final Hashing hashing;
    private final HashingLane lane;
    private final Supplier<Duration> window;
    private final Clock clock;

    /** When each connection asked; changed only through compute. */
    private final Map<UUID, Instant> asked = new ConcurrentHashMap<>();

    public DeleteOwnAccount(
            Sessions sessions,
            AccountRepository accounts,
            Factors factors,
            FactorCheck codes,
            Hashing hashing,
            HashingLane lane,
            Unregister unregister,
            Supplier<Duration> window,
            Clock clock) {
        this.tables = new Tables(sessions, accounts, factors, unregister);
        this.codes = Objects.requireNonNull(codes, "codes");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.window = Objects.requireNonNull(window, "window");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Opens the window for the logged-in player of this connection. */
    public Deleted ask(UUID connection) {
        Optional<Account> account = loggedIn(Objects.requireNonNull(connection, "connection"));
        if (account.isEmpty()) {
            return new Deleted.NotLoggedIn();
        }
        boolean byCode = tables.factors().app(account.get().id()).isPresent();
        if (!byCode && account.get().password() == null) {
            return new Deleted.NoProof();
        }
        Instant now = clock.instant();
        asked.compute(connection, (key, before) -> now);
        return new Deleted.Asked(window.get(), byCode);
    }

    /** Removes the account when the window is open and the proof is right. Hashing runs on the lane. */
    public CompletableFuture<Deleted> confirm(UUID connection, String proof) {
        Objects.requireNonNull(proof, "proof");
        Optional<Account> account = loggedIn(Objects.requireNonNull(connection, "connection"));
        if (account.isEmpty()) {
            return CompletableFuture.completedFuture(new Deleted.NotLoggedIn());
        }
        Instant at = take(connection);
        if (at == null || clock.instant().isAfter(at.plus(window.get()))) {
            return CompletableFuture.completedFuture(new Deleted.NotAsked());
        }
        Account owner = account.get();
        return lane.submit(() -> proves(owner, proof) ? remove(owner) : new Deleted.WrongProof());
    }

    private Deleted remove(Account owner) {
        tables.unregister().remove(owner.name(), Bulletin.By.OWNER);
        return new Deleted.Removed();
    }

    private boolean proves(Account owner, String proof) {
        if (tables.factors().app(owner.id()).isPresent()) {
            return codes.appOrBackup(owner.id(), proof, clock.instant());
        }
        PasswordHash stored =
                tables.accounts().findById(owner.id()).map(Account::password).orElse(null);
        if (stored == null) {
            return false;
        }
        try (Password typed = Password.of(proof)) {
            return !(hashing.verify(typed, stored) instanceof Verification.Wrong);
        }
    }

    private Optional<Account> loggedIn(UUID connection) {
        return tables.sessions()
                .state(connection)
                .filter(AuthState.Authenticated.class::isInstance)
                .map(state -> ((AuthState.Authenticated) state).account());
    }

    private @Nullable Instant take(UUID connection) {
        AtomicReference<@Nullable Instant> taken = new AtomicReference<>();
        asked.compute(connection, (key, at) -> {
            taken.set(at);
            return null;
        });
        return taken.get();
    }
}
