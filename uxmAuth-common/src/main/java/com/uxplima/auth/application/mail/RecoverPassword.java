package com.uxplima.auth.application.mail;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.Wiping;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.BreachedPasswords;
import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Mailer;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthState;

/**
 * A forgotten password. A player at the login asks a code at the linked address, and the code with a new password
 * sets it. Every session of the account ends and every remembered one is forgotten; the player stays at the login
 * and logs in with the new password, so a second factor is still asked. A wrong code counts against the address.
 * The table is read and the password hashed on the hashing lane.
 */
public final class RecoverPassword {

    /** What asking or recovering came to. */
    public sealed interface Outcome {
        /** A code went to the linked address, shown masked, good for this long. */
        record Sent(String masked, Duration lasts) implements Outcome {}

        /** A code went out too recently; another may go after this long. */
        record Cooldown(Duration left) implements Outcome {}

        /** No address is linked to the account. */
        record NoAddress() implements Outcome {}

        /** Mail is off on this server. */
        record MailOff() implements Outcome {}

        /** The mail could not be sent. */
        record MailFailed() implements Outcome {}

        /** Only a player at the login, who has not given the password, recovers one. */
        record NotWaiting() implements Outcome {}

        /** The new password is set. */
        record Recovered() implements Outcome {}

        /** Today's rules refuse the new password; no try of the code was spent. */
        record Rejected(PasswordVerdict why) implements Outcome {}

        /** Not the code, with this many tries left. */
        record Wrong(int attemptsLeft) implements Outcome {}

        /** The code is past its time. */
        record Expired() implements Outcome {}

        /** No code waits. */
        record NothingPending() implements Outcome {}

        /** The last try is spent, and the code with it. */
        record TooManyAttempts() implements Outcome {}

        /** The wrong code banned the address it came from, for this long. */
        record Banned(Duration lasts) implements Outcome {}
    }

    /** Where the password rules and the breach list are read. */
    private record Rules(Supplier<PasswordPolicy> policy, BreachedPasswords breaches) {}

    /** Where the end of every session is said. */
    private record Ending(RememberedSessions remembered, NetworkEvents events) {}

    private final Sessions sessions;
    private final Emails emails;
    private final MailCodes codes;
    private final AccountRepository accounts;
    private final Hashing hashing;
    private final HashingLane lane;
    private final Rules rules;
    private final Ending ending;

    public RecoverPassword(
            Sessions sessions,
            Emails emails,
            MailCodes codes,
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Supplier<PasswordPolicy> policy,
            BreachedPasswords breaches,
            RememberedSessions remembered,
            NetworkEvents events) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.emails = Objects.requireNonNull(emails, "emails");
        this.codes = Objects.requireNonNull(codes, "codes");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.rules = new Rules(Objects.requireNonNull(policy, "policy"), Objects.requireNonNull(breaches, "breaches"));
        this.ending =
                new Ending(Objects.requireNonNull(remembered, "remembered"), Objects.requireNonNull(events, "events"));
    }

    /** Mails a recovery code to the linked address of the account this connection waits to log in to. */
    public CompletableFuture<Outcome> request(UUID connection, Locale language) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(language, "language");
        Optional<Account> account = waiting(connection);
        if (account.isEmpty()) {
            return done(new Outcome.NotWaiting());
        }
        Account owner = account.get();
        return lane.submit(() -> emails.address(owner.id())).thenCompose(to -> {
            if (to.isEmpty()) {
                return done(new Outcome.NoAddress());
            }
            return codes.issue(
                            owner.id(),
                            Emails.Purpose.RECOVERY,
                            to.get(),
                            Mailer.Kind.RECOVERY,
                            language,
                            Map.of("player", owner.name().value()))
                    .thenApply(RecoverPassword::issued);
        });
    }

    private static Outcome issued(MailCodes.Issued issued) {
        return switch (issued) {
            case MailCodes.Issued.Sent sent -> new Outcome.Sent(sent.to().masked(), sent.lasts());
            case MailCodes.Issued.Cooldown cooldown -> new Outcome.Cooldown(cooldown.left());
            case MailCodes.Issued.Off off -> new Outcome.MailOff();
            case MailCodes.Issued.Failed failed -> new Outcome.MailFailed();
            case MailCodes.Issued.Live live -> throw new IllegalStateException("a recovery always sends a fresh code");
        };
    }

    /**
     * Sets the new password when the code that went to the address is right, typed from this address. A password
     * the rules refuse is said before the code is tried. The new password is wiped once the answer is known.
     */
    public CompletableFuture<Outcome> recover(
            UUID connection, String code, Password fresh, String address, String host) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(fresh, "fresh");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(host, "host");
        return Wiping.after(() -> attempt(connection, code, fresh, address, host), fresh);
    }

    private CompletableFuture<Outcome> attempt(
            UUID connection, String code, Password fresh, String address, String host) {
        Optional<Account> account = waiting(connection);
        if (account.isEmpty()) {
            return done(new Outcome.NotWaiting());
        }
        PasswordVerdict verdict = rules.policy().get().check(fresh);
        if (!(verdict instanceof PasswordVerdict.Accepted)) {
            return done(new Outcome.Rejected(verdict));
        }
        UUID id = account.get().id();
        return lane.submit(() -> rules.breaches().breached(fresh)).thenCompose(breached -> {
            if (breached) {
                return done(new Outcome.Rejected(new PasswordVerdict.Breached()));
            }
            return codes.check(id, Emails.Purpose.RECOVERY, code, address, host)
                    .thenCompose(checked -> checked(connection, id, fresh, checked));
        });
    }

    private CompletableFuture<Outcome> checked(UUID connection, UUID id, Password fresh, MailCodes.Checked checked) {
        return switch (checked) {
            case MailCodes.Checked.Right right -> lane.submit(() -> set(connection, id, fresh));
            case MailCodes.Checked.Wrong wrong -> done(new Outcome.Wrong(wrong.attemptsLeft()));
            case MailCodes.Checked.Expired expired -> done(new Outcome.Expired());
            case MailCodes.Checked.NothingPending nothing -> done(new Outcome.NothingPending());
            case MailCodes.Checked.TooManyAttempts tooMany -> done(new Outcome.TooManyAttempts());
            case MailCodes.Checked.Banned banned -> done(new Outcome.Banned(banned.lasts()));
        };
    }

    /** The new hash replaces whatever the row holds now, and every session of the account ends. */
    private Outcome set(UUID connection, UUID id, Password fresh) {
        PasswordHash hash = hashing.hash(fresh);
        Account updated = null;
        for (int tried = 0; tried < 3 && updated == null; tried++) {
            Account current = accounts.findById(id).orElse(null);
            if (current == null) {
                return new Outcome.NothingPending();
            }
            if (accounts.replacePassword(id, current.password(), hash)) {
                updated = current.withPassword(hash);
            }
        }
        if (updated == null) {
            return new Outcome.NothingPending();
        }
        Account now = updated;
        sessions.update(
                connection,
                state -> state instanceof AuthState.Registered held
                        ? new AuthState.Registered(
                                now, Attempts.fresh(held.attempts().max()))
                        : state);
        ending.remembered().forget(id);
        ending.events().publish(new Bulletin.SessionsEnded(id, Optional.empty()));
        return new Outcome.Recovered();
    }

    /** The account a connection waits to give the password of. */
    private Optional<Account> waiting(UUID connection) {
        return sessions.state(connection)
                .filter(AuthState.Registered.class::isInstance)
                .map(state -> ((AuthState.Registered) state).account());
    }

    private static CompletableFuture<Outcome> done(Outcome outcome) {
        return CompletableFuture.completedFuture(outcome);
    }
}
