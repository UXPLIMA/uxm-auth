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

import com.uxplima.auth.application.LogIn;
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
 * costs an ask, and counts as a wrong login does: against the address it came from and against the account, which
 * a lock then shuts to every proof for a while. The command is no oracle for the password.
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

        /** The wrong proof banned the address it came from, for this long; the platform sends the player away. */
        record Banned(Duration lasts) implements Deleted {
            public Banned {
                Objects.requireNonNull(lasts, "lasts");
            }
        }

        /** Too many wrong passwords and codes were typed for the account lately: no proof is checked for this long. */
        record Locked(Duration left) implements Deleted {
            public Locked {
                Objects.requireNonNull(left, "left");
            }
        }
    }

    /** Where a wrong proof counts: against its address, and against the account it was typed for. */
    public record Guards(LogIn.Failures failures, LogIn.Lockout lockout) {
        public Guards {
            Objects.requireNonNull(failures, "failures");
            Objects.requireNonNull(lockout, "lockout");
        }
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
    private final Guards guards;

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
            Clock clock,
            Guards guards) {
        this.guards = Objects.requireNonNull(guards, "guards");
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
        letGoClosed(now);
        asked.compute(connection, (key, before) -> now);
        return new Deleted.Asked(window.get(), byCode);
    }

    // An ask nobody confirms would stay for good: the player may leave, and nothing else visits the map.
    private void letGoClosed(Instant now) {
        Duration open = window.get();
        for (UUID key : asked.keySet()) {
            asked.compute(key, (id, at) -> at == null || now.isAfter(at.plus(open)) ? null : at);
        }
    }

    /**
     * Removes the account when the window is open and the proof, typed from this address, is right. Hashing runs on
     * the lane.
     */
    public CompletableFuture<Deleted> confirm(UUID connection, String proof, String address) {
        Objects.requireNonNull(proof, "proof");
        Objects.requireNonNull(address, "address");
        Optional<Account> account = loggedIn(Objects.requireNonNull(connection, "connection"));
        if (account.isEmpty()) {
            return CompletableFuture.completedFuture(new Deleted.NotLoggedIn());
        }
        Instant at = take(connection);
        if (at == null || clock.instant().isAfter(at.plus(window.get()))) {
            return CompletableFuture.completedFuture(new Deleted.NotAsked());
        }
        Account owner = account.get();
        String host =
                tables.sessions().held(connection).map(Sessions.Held::host).orElse("");
        return lane.submit(() -> proved(owner, proof, address, host));
    }

    /** Takes a try at the account before the proof is checked, unless it is locked, and removes it on a right one. */
    private Deleted proved(Account owner, String proof, String address, String host) {
        LogIn.Lockout.Try.Taken taken;
        switch (guards.lockout().take(owner.id())) {
            case LogIn.Lockout.Try.Refused refused -> {
                return new Deleted.Locked(refused.left());
            }
            case LogIn.Lockout.Try.Taken took -> taken = took;
        }
        if (proves(owner, proof)) {
            guards.lockout().right(taken);
            return remove(owner);
        }
        Optional<Duration> banned = guards.failures().failed(address, host);
        if (banned.isPresent()) {
            return new Deleted.Banned(banned.get());
        }
        return taken.locks().<Deleted>map(Deleted.Locked::new).orElseGet(Deleted.WrongProof::new);
    }

    /** How many asks are held, for the test that proves a closed one is let go. */
    int open() {
        return asked.size();
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
