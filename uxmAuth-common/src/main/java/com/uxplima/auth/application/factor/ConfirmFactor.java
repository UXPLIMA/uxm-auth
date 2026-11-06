package com.uxplima.auth.application.factor;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.state.AuthState;
import org.jspecify.annotations.Nullable;

/**
 * Takes the code of the second step. A try is spent on the session before the code is checked, so codes sent
 * together cannot try more than the limit allows; a code is taken once across every server, inside its window; a
 * wrong one counts against the address, against the account wherever it came from, and toward the captcha of the
 * name, as a wrong password does; a backup code is spent the same way. A locked account checks no code. The login
 * is recorded only once the code passed. The table is read and backup codes are hashed here, so this runs off the
 * server thread.
 */
public final class ConfirmFactor {

    /** What a code came to. */
    public sealed interface Result {
        /** The code passed; the connection stands here now. */
        record LoggedIn(AuthState state) implements Result {
            public LoggedIn {
                Objects.requireNonNull(state, "state");
            }
        }

        /** Not the code, with this many tries left. */
        record Wrong(int attemptsLeft) implements Result {}

        /** The last try is spent. */
        record TooManyAttempts() implements Result {}

        /** The wrong code banned the address it came from, for this long. */
        record Banned(Duration lasts) implements Result {
            public Banned {
                Objects.requireNonNull(lasts, "lasts");
            }
        }

        /** Too many wrong passwords and codes were typed for the account lately: no code is checked for this long. */
        record Locked(Duration left) implements Result {
            public Locked {
                Objects.requireNonNull(left, "left");
            }
        }

        /** The connection waits for no code. */
        record NotWaiting() implements Result {}

        /** The player left. */
        record Gone() implements Result {}
    }

    private final AccountRepository accounts;
    private final Sessions sessions;
    private final FactorCheck check;
    private final LogIn.Failures failures;
    private final LogIn.Lockout lockout;
    private final LogIn.Captcha captcha;
    private final Supplier<Instant> clock;

    /**
     * @param lockout where every code counts against the account it was typed for, and locks it after enough
     * @param captcha where a wrong code counts toward the captcha of the name, as a wrong password does
     */
    public ConfirmFactor(
            AccountRepository accounts,
            Sessions sessions,
            FactorCheck check,
            LogIn.Failures failures,
            LogIn.Lockout lockout,
            LogIn.Captcha captcha,
            Supplier<Instant> clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.check = Objects.requireNonNull(check, "check");
        this.failures = Objects.requireNonNull(failures, "failures");
        this.lockout = Objects.requireNonNull(lockout, "lockout");
        this.captcha = Objects.requireNonNull(captcha, "captcha");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Answers the second step of this connection with a code typed from this address. */
    public Result confirm(UUID connection, String code, String address) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(address, "address");
        Sessions.Held held = sessions.held(connection).orElse(null);
        if (held == null) {
            return new Result.Gone();
        }
        if (!(held.state() instanceof AuthState.SecondFactor waiting)
                || waiting.attempts().exhausted()) {
            return refusal(held.state());
        }
        LogIn.Lockout.Try.Taken taken;
        switch (lockout.take(waiting.account(), address, held.host())) {
            case LogIn.Lockout.Try.Refused refused -> {
                return new Result.Locked(refused.left());
            }
            case LogIn.Lockout.Try.Taken took -> taken = took;
        }
        AtomicReference<AuthState.SecondFactor> reserved = new AtomicReference<>();
        AuthState after = sessions.update(connection, held.generation(), current -> spend(current, reserved))
                .orElse(null);
        AuthState.SecondFactor spent = reserved.get();
        if (spent == null) {
            lockout.right(taken);
            return refusal(after);
        }
        Instant now = clock.get();
        if (!check.passes(spent.account().id(), code, now)) {
            captcha.failed(spent.account().name());
            return wrong(spent, address, held.host(), taken);
        }
        lockout.passed(taken.account());
        return logIn(connection, held.generation(), spent, address, now);
    }

    /** Spends one try on a connection that waits for a code and has one left. */
    private static AuthState spend(AuthState current, AtomicReference<AuthState.SecondFactor> reserved) {
        if (current instanceof AuthState.SecondFactor waiting
                && !waiting.attempts().exhausted()) {
            AuthState.SecondFactor spent = waiting.failed();
            reserved.set(spent);
            return spent;
        }
        return current;
    }

    private static Result refusal(@Nullable AuthState after) {
        if (after == null) {
            return new Result.Gone();
        }
        return after instanceof AuthState.SecondFactor ? new Result.TooManyAttempts() : new Result.NotWaiting();
    }

    private Result wrong(AuthState.SecondFactor spent, String address, String host, LogIn.Lockout.Try.Taken taken) {
        Optional<Duration> banned = failures.failed(address, host);
        if (banned.isPresent()) {
            return new Result.Banned(banned.get());
        }
        Optional<Duration> locked = taken.locks();
        if (locked.isPresent()) {
            return new Result.Locked(locked.get());
        }
        return spent.attempts().exhausted()
                ? new Result.TooManyAttempts()
                : new Result.Wrong(spent.attempts().left());
    }

    private Result logIn(UUID connection, long generation, AuthState.SecondFactor spent, String address, Instant now) {
        accounts.recordLogin(spent.account().id(), now, address);
        Account updated = spent.account().loggedIn(now, address);
        AuthState next = spent.mustReplace() == null
                ? new AuthState.Authenticated(updated, spent.method())
                : new AuthState.Replacing(updated, spent.mustReplace());
        return sessions.update(connection, generation, state -> state instanceof AuthState.SecondFactor ? next : state)
                .<Result>map(state -> new Result.LoggedIn(next))
                .orElseGet(Result.Gone::new);
    }
}
