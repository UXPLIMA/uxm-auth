package com.uxplima.auth.application.factor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.domain.factor.Totp;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.name.PlayerName;

/**
 * Turns an authenticator app on and off. A setup draws a fresh secret and keeps it in this server's memory only;
 * nothing is stored until a code from the app proves the player set it up. Turning it off takes a code from the
 * app, and a wrong one counts against the address as a wrong password does.
 */
public final class SetUpTotp {

    /**
     * What the operator chose for the app.
     *
     * @param issuer the name an app lists the account under
     * @param label the account's line in the app, with the player's name where it says {player}
     * @param totp how codes are made and taken
     * @param setupTime how long a setup waits for its first code
     */
    public record Settings(String issuer, String label, Totp totp, Duration setupTime) {
        public Settings {
            Objects.requireNonNull(issuer, "issuer");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(totp, "totp");
            Objects.requireNonNull(setupTime, "setupTime");
            if (issuer.isBlank()) {
                throw new IllegalArgumentException("second-factor.issuer must not be empty");
            }
            if (label.isBlank()) {
                throw new IllegalArgumentException("second-factor.label must not be empty");
            }
            if (setupTime.isNegative() || setupTime.isZero()) {
                throw new IllegalArgumentException("second-factor.setup-minutes must be at least 1");
            }
        }
    }

    /** What a setup, a confirmation or a turning off came to. */
    public sealed interface Outcome {
        /** A setup began: the secret and the link to show, only now. */
        record Started(TotpSecret secret, String link) implements Outcome {
            public Started {
                Objects.requireNonNull(secret, "secret");
                Objects.requireNonNull(link, "link");
            }
        }

        /** The app is on. */
        record On() implements Outcome {}

        /** The app is off. */
        record Off() implements Outcome {}

        /** The code was not the app's code now, or was spent already. */
        record Wrong() implements Outcome {}

        /** The wrong code banned the address it came from, for this long. */
        record Banned(Duration time) implements Outcome {
            public Banned {
                Objects.requireNonNull(time, "time");
            }
        }

        /** No setup waits for a code. */
        record NothingStarted() implements Outcome {}

        /** The app is on already; it goes off before a new one is set up. */
        record AlreadyOn() implements Outcome {}

        /** The app is not on. */
        record NotOn() implements Outcome {}
    }

    /** A setup waiting for its first code. */
    private record Pending(TotpSecret secret, Instant until) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Factors factors;
    private final Supplier<Settings> settings;
    private final LogIn.Failures failures;
    private final RandomGenerator random;
    private final Supplier<Instant> clock;

    /** @param random where secrets come from; a server passes a secure one */
    public SetUpTotp(
            Factors factors,
            Supplier<Settings> settings,
            LogIn.Failures failures,
            RandomGenerator random,
            Supplier<Instant> clock) {
        this.factors = Objects.requireNonNull(factors, "factors");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.failures = Objects.requireNonNull(failures, "failures");
        this.random = Objects.requireNonNull(random, "random");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Whether the account has the app on. */
    public boolean active(UUID account) {
        return factors.app(Objects.requireNonNull(account, "account")).isPresent();
    }

    /** Begins a setup, in place of any earlier one: {@link Outcome.Started}, or {@link Outcome.AlreadyOn}. */
    public Outcome start(UUID account, PlayerName name) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(name, "name");
        if (active(account)) {
            return new Outcome.AlreadyOn();
        }
        Settings now = settings.get();
        TotpSecret secret = new TotpSecret(Totp.newSecret(random));
        Instant until = clock.get().plus(now.setupTime());
        pending.compute(account, (id, before) -> new Pending(secret, until));
        return new Outcome.Started(secret, link(now, secret, name));
    }

    /** Takes the first code of the setup: {@link Outcome.On}, {@link Outcome.Wrong} or {@link Outcome.NothingStarted}. */
    public Outcome confirm(UUID account, String typed) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(typed, "typed");
        Instant now = clock.get();
        Pending waiting = pending.get(account);
        if (waiting == null || !waiting.until().isAfter(now)) {
            pending.compute(account, (id, before) -> before == waiting ? null : before);
            return new Outcome.NothingStarted();
        }
        OptionalLong step = settings.get().totp().verify(waiting.secret().bytes(), typed, now, -1);
        if (step.isEmpty() || !pending.remove(account, waiting)) {
            return step.isEmpty() ? new Outcome.Wrong() : new Outcome.NothingStarted();
        }
        factors.setApp(account, waiting.secret());
        factors.useStep(account, step.getAsLong());
        return new Outcome.On();
    }

    /**
     * Turns the app off with a code from it, and the backup codes with it: {@link Outcome.Off},
     * {@link Outcome.Wrong}, {@link Outcome.Banned} or {@link Outcome.NotOn}.
     */
    public Outcome off(UUID account, String typed, String address, String host) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(typed, "typed");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(host, "host");
        Optional<Factors.App> app = factors.app(account);
        if (app.isEmpty()) {
            return new Outcome.NotOn();
        }
        OptionalLong step = settings.get()
                .totp()
                .verify(
                        app.get().secret().bytes(),
                        typed,
                        clock.get(),
                        app.get().lastStep());
        if (step.isEmpty() || !factors.useStep(account, step.getAsLong())) {
            return failures.failed(address, host)
                    .<Outcome>map(Outcome.Banned::new)
                    .orElseGet(Outcome.Wrong::new);
        }
        factors.removeApp(account);
        factors.setBackupCodes(account, List.of());
        return new Outcome.Off();
    }

    /** The player left: a setup they had open goes. */
    public void forget(UUID account) {
        pending.compute(Objects.requireNonNull(account, "account"), (id, before) -> null);
    }

    private static String link(Settings settings, TotpSecret secret, PlayerName name) {
        String issuer = encode(settings.issuer());
        String label = encode(settings.label().replace("{player}", name.value()));
        return "otpauth://totp/" + issuer + ":" + label + "?secret=" + secret.base32() + "&issuer=" + issuer
                + "&algorithm=SHA1&digits=" + settings.totp().digits() + "&period="
                + settings.totp().periodSeconds();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
