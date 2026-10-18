package com.uxplima.auth.application.mail;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Mailer;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.password.Password;
import org.jspecify.annotations.Nullable;

/**
 * Codes sent by mail. An account holds one code per purpose, sent no faster than the cooldown, good for a while
 * and for a few tries, and taken once. A try is spent before the code is checked, so codes typed together cannot
 * try more than the limit; a wrong one counts against the address as a wrong password does. A code is hashed as a
 * password is, so the work runs on the hashing lane, and the mail on the mailer's own threads.
 */
public final class MailCodes {

    /**
     * What the operator chose for mailed codes.
     *
     * @param codeTime how long a code is good for
     * @param attempts how many tries a code takes
     * @param cooldown how long before the same purpose sends another code
     * @param codeLength how many digits a code has
     */
    public record Settings(Duration codeTime, int attempts, Duration cooldown, int codeLength) {
        public Settings {
            Objects.requireNonNull(codeTime, "codeTime");
            Objects.requireNonNull(cooldown, "cooldown");
            if (codeTime.isNegative() || codeTime.isZero()) {
                throw new IllegalArgumentException("email.code-minutes must be at least 1");
            }
            if (attempts < 1) {
                throw new IllegalArgumentException("email.code-attempts must be at least 1, not " + attempts);
            }
            if (cooldown.isNegative()) {
                throw new IllegalArgumentException("email.cooldown-seconds must not be negative");
            }
            if (codeLength < 4 || codeLength > 10) {
                throw new IllegalArgumentException("email.code-length must be 4 to 10, not " + codeLength);
            }
        }
    }

    /** What sending a code came to. */
    public sealed interface Issued {
        /** The code went to this address, good for this long. */
        record Sent(EmailAddress to, Duration lasts) implements Issued {}

        /** A code of this purpose went out too recently; another may go after this long. */
        record Cooldown(Duration left) implements Issued {}

        /** A code of this purpose is out and still good; nothing was sent. */
        record Live(EmailAddress to) implements Issued {}

        /** Mail is off. */
        record Off() implements Issued {}

        /** The mail could not be sent. */
        record Failed() implements Issued {}
    }

    /** What a typed code came to. */
    public sealed interface Checked {
        /** The code passed and is spent; it was sent to this address. */
        record Right(EmailAddress to) implements Checked {}

        /** Not the code, with this many tries left. */
        record Wrong(int attemptsLeft) implements Checked {}

        /** The code is past its time. */
        record Expired() implements Checked {}

        /** No code of this purpose is out. */
        record NothingPending() implements Checked {}

        /** The last try is spent, and the code with it. */
        record TooManyAttempts() implements Checked {}

        /** The wrong code banned the address it came from, for this long. */
        record Banned(Duration lasts) implements Checked {}
    }

    private final Emails emails;
    private final Mailer mailer;
    private final Hashing hashing;
    private final HashingLane lane;
    private final Supplier<Settings> settings;
    private final LogIn.Failures failures;
    private final RandomGenerator random;
    private final Supplier<Instant> clock;

    /** @param random where codes come from; a server passes a secure one */
    public MailCodes(
            Emails emails,
            Mailer mailer,
            Hashing hashing,
            HashingLane lane,
            Supplier<Settings> settings,
            LogIn.Failures failures,
            RandomGenerator random,
            Supplier<Instant> clock) {
        this.emails = Objects.requireNonNull(emails, "emails");
        this.mailer = Objects.requireNonNull(mailer, "mailer");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.failures = Objects.requireNonNull(failures, "failures");
        this.random = Objects.requireNonNull(random, "random");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Sends a fresh code of this purpose to the address, in place of any before it, unless one went out inside
     * the cooldown. The mail carries {@code code} and {@code minutes} beside the values given.
     */
    public CompletableFuture<Issued> issue(
            UUID account,
            Emails.Purpose purpose,
            EmailAddress to,
            Mailer.Kind kind,
            Locale language,
            Map<String, String> values) {
        return issue(account, purpose, to, kind, language, values, false);
    }

    /**
     * Sends a code of this purpose only when none is out that is still good, so a player typing the one they got
     * is never handed a new one under it: {@link Issued.Live} then.
     */
    public CompletableFuture<Issued> issueUnlessLive(
            UUID account,
            Emails.Purpose purpose,
            EmailAddress to,
            Mailer.Kind kind,
            Locale language,
            Map<String, String> values) {
        return issue(account, purpose, to, kind, language, values, true);
    }

    private CompletableFuture<Issued> issue(
            UUID account,
            Emails.Purpose purpose,
            EmailAddress to,
            Mailer.Kind kind,
            Locale language,
            Map<String, String> values,
            boolean keepLive) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(language, "language");
        Map<String, String> filled = new HashMap<>(Objects.requireNonNull(values, "values"));
        Settings now = settings.get();
        String code = digits(now.codeLength());
        return lane.submit(() -> stored(account, purpose, to, hash(code), now, keepLive))
                .thenCompose(stored -> {
                    if (stored.isPresent()) {
                        return CompletableFuture.completedFuture(stored.get());
                    }
                    filled.put("code", code);
                    filled.put("minutes", String.valueOf(now.codeTime().toMinutes()));
                    return mailer.send(kind, to, language, filled)
                            .thenApply(sent -> sent(account, purpose, to, sent, now));
                });
    }

    /** Nothing when a fresh code is stored; the cooldown, or the live code, that refused one otherwise. */
    private Optional<Issued> stored(
            UUID account, Emails.Purpose purpose, EmailAddress to, PasswordHash hash, Settings now, boolean keepLive) {
        Instant at = clock.get();
        AtomicReference<@Nullable Issued> refused = new AtomicReference<>();
        emails.changeCode(account, purpose, before -> {
            refused.set(refusal(before, at, now, keepLive));
            if (refused.get() != null) {
                return before;
            }
            return Optional.of(new Emails.Code(to, hash, at.plus(now.codeTime()), now.attempts(), at));
        });
        return Optional.ofNullable(refused.get());
    }

    private static @Nullable Issued refusal(Optional<Emails.Code> before, Instant at, Settings now, boolean keepLive) {
        if (before.isEmpty()) {
            return null;
        }
        Emails.Code code = before.get();
        if (keepLive && code.expires().isAfter(at) && code.attemptsLeft() > 0) {
            return new Issued.Live(code.to());
        }
        Instant free = code.sent().plus(now.cooldown());
        return free.isAfter(at) ? new Issued.Cooldown(Duration.between(at, free)) : null;
    }

    private Issued sent(UUID account, Emails.Purpose purpose, EmailAddress to, Mailer.Outcome outcome, Settings now) {
        if (outcome == Mailer.Outcome.SENT) {
            return new Issued.Sent(to, now.codeTime());
        }
        // Nothing reached the player, so nothing holds them back from asking again.
        emails.changeCode(account, purpose, before -> Optional.empty());
        return outcome == Mailer.Outcome.OFF ? new Issued.Off() : new Issued.Failed();
    }

    /**
     * Takes a code of this purpose as a second factor: a try is spent, and the code is gone when it passes or its
     * last try is spent. Nothing counts against the address here; the step that asked counts its own failures.
     * Runs on the caller's thread, which is the hashing lane's.
     */
    public boolean takes(UUID account, Emails.Purpose purpose, String typed) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(purpose, "purpose");
        Checked checked = reserved(
                account, purpose, Objects.requireNonNull(typed, "typed").strip());
        return checked instanceof Checked.Right;
    }

    /** Checks a typed code of this purpose, typed from this address. */
    public CompletableFuture<Checked> check(
            UUID account, Emails.Purpose purpose, String typed, String address, String host) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(typed, "typed");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(host, "host");
        return lane.submit(() -> checked(account, purpose, typed.strip(), address, host));
    }

    private Checked checked(UUID account, Emails.Purpose purpose, String typed, String address, String host) {
        Checked checked = reserved(account, purpose, typed);
        if (!(checked instanceof Checked.Wrong wrong)) {
            return checked;
        }
        Optional<Duration> banned = failures.failed(address, host);
        if (banned.isPresent()) {
            return new Checked.Banned(banned.get());
        }
        return wrong.attemptsLeft() <= 0 ? new Checked.TooManyAttempts() : wrong;
    }

    /** Spends a try and checks the code: right and taken, wrong with the tries left, or why there was none. */
    private Checked reserved(UUID account, Emails.Purpose purpose, String typed) {
        Instant at = clock.get();
        AtomicReference<Emails.Code> found = new AtomicReference<>();
        emails.changeCode(account, purpose, before -> {
            found.set(before.orElse(null));
            if (before.isEmpty()
                    || !before.get().expires().isAfter(at)
                    || before.get().attemptsLeft() <= 0) {
                return Optional.empty();
            }
            return Optional.of(before.get().spent());
        });
        Emails.Code before = found.get();
        if (before == null) {
            return new Checked.NothingPending();
        }
        if (!before.expires().isAfter(at)) {
            return new Checked.Expired();
        }
        if (before.attemptsLeft() <= 0) {
            return new Checked.TooManyAttempts();
        }
        return matches(typed, before.hash()) ? taken(account, purpose, before) : wrong(account, purpose, before);
    }

    private Checked taken(UUID account, Emails.Purpose purpose, Emails.Code before) {
        AtomicBoolean took = new AtomicBoolean();
        emails.changeCode(account, purpose, now -> {
            boolean same = now.isPresent() && now.get().hash().equals(before.hash());
            took.set(same);
            return same ? Optional.empty() : now;
        });
        return took.get() ? new Checked.Right(before.to()) : new Checked.NothingPending();
    }

    /** A wrong code: the last try takes the code with it. */
    private Checked wrong(UUID account, Emails.Purpose purpose, Emails.Code before) {
        int left = before.attemptsLeft() - 1;
        if (left <= 0) {
            emails.changeCode(
                    account, purpose, now -> now.filter(code -> !code.hash().equals(before.hash())));
        }
        return new Checked.Wrong(left);
    }

    private boolean matches(String typed, PasswordHash stored) {
        try (Password code = Password.of(typed)) {
            return !(hashing.verify(code, stored) instanceof Verification.Wrong);
        }
    }

    private PasswordHash hash(String code) {
        try (Password password = Password.of(code)) {
            return hashing.hash(password);
        }
    }

    private String digits(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append((char) ('0' + random.nextInt(10)));
        }
        return code.toString();
    }
}
