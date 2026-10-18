package com.uxplima.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.BreachedPasswords;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import org.jspecify.annotations.Nullable;

/**
 * Makes an account for a connection that has none, and logs it in.
 *
 * <p>Everything cheap is decided here, on the caller's thread: the connection, the confirmation, the policy.
 * Only a password that will be kept costs a hash, and the hash runs on the lane.
 */
public final class RegisterAccount {

    private static final long NO_OPENING = -1;

    /** Whether an address, through the host the player typed, made all the password accounts it may for now. */
    @FunctionalInterface
    public interface Cap {
        boolean full(String address, String host);
    }

    /** Whether a captcha stands before the registration of the connection with this id. */
    @FunctionalInterface
    public interface Captcha {
        boolean blocks(UUID connection);
    }

    private final AccountRepository accounts;
    private final Hashing hashing;
    private final HashingLane lane;
    private final Sessions sessions;
    private final Supplier<PasswordPolicy> policy;
    private final Clock clock;
    private final Cap cap;
    private final BreachedPasswords breaches;
    private final Captcha captcha;

    public RegisterAccount(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Supplier<PasswordPolicy> policy,
            Clock clock) {
        this(accounts, hashing, lane, sessions, policy, clock, (address, host) -> false);
    }

    /** @param cap whether an address made all the accounts it may, asked on the lane before the hash */
    public RegisterAccount(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Supplier<PasswordPolicy> policy,
            Clock clock,
            Cap cap) {
        this(accounts, hashing, lane, sessions, policy, clock, cap, BreachedPasswords.NONE);
    }

    /** @param breaches whether a password is known from a breach, asked on the lane before the hash */
    public RegisterAccount(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Supplier<PasswordPolicy> policy,
            Clock clock,
            Cap cap,
            BreachedPasswords breaches) {
        this(accounts, hashing, lane, sessions, policy, clock, cap, breaches, connection -> false);
    }

    /** @param captcha whether a captcha stands before a registration, asked before any password is looked at */
    public RegisterAccount(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Supplier<PasswordPolicy> policy,
            Clock clock,
            Cap cap,
            BreachedPasswords breaches,
            Captcha captcha) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cap = Objects.requireNonNull(cap, "cap");
        this.breaches = Objects.requireNonNull(breaches, "breaches");
        this.captcha = Objects.requireNonNull(captcha, "captcha");
    }

    /** Both passwords are wiped once the answer is known, whatever it is. */
    public CompletableFuture<RegisterResult> run(
            UUID connection, PlayerName name, Password password, Password confirm, String address) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(confirm, "confirm");
        Objects.requireNonNull(address, "address");
        return Wiping.after(() -> attempt(connection, NO_OPENING, name, password, confirm, address), password, confirm);
    }

    /**
     * The same, bound to one opening of the session: when another connection under this id opened it again
     * since, the answer is gone and no account is made.
     */
    public CompletableFuture<RegisterResult> run(
            UUID connection, long opening, PlayerName name, Password password, Password confirm, String address) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(confirm, "confirm");
        Objects.requireNonNull(address, "address");
        return Wiping.after(() -> attempt(connection, opening, name, password, confirm, address), password, confirm);
    }

    private CompletableFuture<RegisterResult> attempt(
            UUID connection, long opening, PlayerName name, Password password, Password confirm, String address) {
        Sessions.Held held = sessions.held(connection)
                .filter(found -> opening == NO_OPENING || found.generation() == opening)
                .orElse(null);
        if (held == null) {
            return CompletableFuture.completedFuture(new RegisterResult.Gone());
        }
        if (!(held.state() instanceof AuthState.Unregistered unregistered)) {
            return CompletableFuture.completedFuture(new RegisterResult.AlreadyRegistered());
        }
        RegisterResult refused = refusal(connection, password, confirm);
        if (refused != null) {
            return CompletableFuture.completedFuture(refused);
        }
        long generation = held.generation();
        boolean unverified = unregistered.unverified();
        return lane.submit(() -> {
            if (cap.full(address, held.host())) {
                return new RegisterResult.TooManyAccounts();
            }
            if (breaches.breached(password)) {
                return new RegisterResult.Rejected(new PasswordVerdict.Breached());
            }
            return store(connection, generation, name, hashing.hash(password), address, unverified);
        });
    }

    /** Why a registration is refused before anything costs a hash: the captcha, the confirmation, the policy. */
    private @Nullable RegisterResult refusal(UUID connection, Password password, Password confirm) {
        if (captcha.blocks(connection)) {
            return new RegisterResult.CaptchaNeeded();
        }
        if (!password.sameAs(confirm)) {
            return new RegisterResult.Mismatch();
        }
        PasswordVerdict verdict = policy.get().check(password);
        return verdict instanceof PasswordVerdict.Accepted ? null : new RegisterResult.Rejected(verdict);
    }

    private RegisterResult store(
            UUID connection, long generation, PlayerName name, PasswordHash hash, String address, boolean unverified) {
        if (!sessions.isOpen(connection, generation)) {
            return new RegisterResult.Gone();
        }
        Instant now = clock.instant();
        // The connection's UUID is the account's: the one the server gave this player. The schemes that
        // choose another come with the name rules, through a port of their own.
        Account made = new Account(connection, name, AccountKind.CRACKED, null, hash, now, address, now, address);
        // Taken while no premium source could say who owns the name: the next connection asks again.
        Account account = unverified ? made.asUnverified() : made;
        return switch (accounts.insert(account)) {
            case AccountRepository.NameTaken taken -> new RegisterResult.NameTaken(taken.stored());
            case AccountRepository.Inserted inserted ->
                sessions.update(
                                connection,
                                current -> current instanceof AuthState.Unregistered
                                        ? new AuthState.Authenticated(account, AuthMethod.REGISTERED)
                                        : current)
                        .<RegisterResult>map(after -> new RegisterResult.Created(account))
                        .orElseGet(RegisterResult.Gone::new);
        };
    }
}
