package com.uxplima.auth.application.protection;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.protection.CaptchaPicture;
import org.jspecify.annotations.Nullable;

/**
 * The captcha that stands before a registration, before a login once a name failed so often, or both.
 *
 * <p>Each showing draws a fresh code, and only the code of the latest showing answers it. Any answer spends the
 * code, right or wrong, so a guess costs a new showing. A solved captcha holds for the connection that solved it
 * and goes with it: a reconnect starts over. The failures of a name live in this server's memory and are let go
 * once the name stayed quiet for the memory, or logged in.
 */
public final class Captchas implements LogIn.Captcha {

    /** How often a failure also lets go of the names that went quiet. */
    private static final long SWEEP_EVERY = 1024;

    /** When a captcha is asked. */
    public enum When {
        OFF,
        REGISTER,
        FAILURES,
        BOTH;

        boolean atRegistration() {
            return this == REGISTER || this == BOTH;
        }

        boolean afterFailures() {
            return this == FAILURES || this == BOTH;
        }
    }

    /** How a Java player reads the code: a dialog, or a map in the hand. Bedrock always reads a form. */
    public enum JavaScreen {
        DIALOG,
        MAP
    }

    /**
     * What the operator chose for the captcha.
     *
     * @param when when a captcha is asked
     * @param afterFailures the wrong passwords of one name after which its logins wait for a captcha
     * @param length how many characters a code has
     * @param characters what a code is drawn from
     * @param failureMemory how long the failures of a quiet name are kept
     * @param java how a Java player reads the code
     */
    public record Settings(
            When when, int afterFailures, int length, String characters, Duration failureMemory, JavaScreen java) {
        public Settings {
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(characters, "characters");
            Objects.requireNonNull(failureMemory, "failureMemory");
            Objects.requireNonNull(java, "java");
            if (afterFailures < 1) {
                throw new IllegalArgumentException("captcha.after-failures must be at least 1, got " + afterFailures);
            }
            if (length < 3 || length > CaptchaPicture.LONGEST) {
                throw new IllegalArgumentException("captcha.length must be 3 to " + CaptchaPicture.LONGEST
                        + ", the most a map shows legibly; got " + length);
            }
            if (characters.isEmpty() || !CaptchaPicture.drawable(characters)) {
                throw new IllegalArgumentException(
                        "captcha.characters must be capital letters A to Z and digits 0 to 9, got: " + characters);
            }
            if (failureMemory.isNegative() || failureMemory.isZero()) {
                throw new IllegalArgumentException("captcha.failure-memory-minutes must be at least 1");
            }
        }
    }

    /** The code of the latest showing to one connection, if it is still unanswered, and whether one was solved. */
    private record Showing(@Nullable String code, boolean solved) {}

    /** The failures of one name, and when the latest came. */
    private record Tally(int count, Instant last) {}

    private final Map<UUID, Showing> showings = new ConcurrentHashMap<>();
    private final Map<String, Tally> tallies = new ConcurrentHashMap<>();
    private final AtomicLong failures = new AtomicLong();
    private final Supplier<Settings> settings;
    private final RandomGenerator random;
    private final Supplier<Instant> clock;

    /** A captcha that is never asked, for a part built without one. */
    public static Captchas off() {
        Settings off =
                new Settings(When.OFF, 3, 5, "ABCDEFGHJKMNPQRSTUVWXYZ23456789", Duration.ofHours(1), JavaScreen.DIALOG);
        return new Captchas(() -> off, new SecureRandom(), Instant::now);
    }

    /** @param random where codes come from; a server passes a secure one */
    public Captchas(Supplier<Settings> settings, RandomGenerator random, Supplier<Instant> clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.random = Objects.requireNonNull(random, "random");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Whether this connection must solve a captcha before it may register. */
    public boolean blocksRegistration(UUID connection) {
        Objects.requireNonNull(connection, "connection");
        return settings.get().when().atRegistration() && !solved(connection);
    }

    @Override
    public boolean blocks(UUID connection, PlayerName name) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(name, "name");
        Settings now = settings.get();
        if (!now.when().afterFailures() || solved(connection)) {
            return false;
        }
        Tally tally = tallies.get(key(name));
        return tally != null && fresh(tally, now) && tally.count() >= now.afterFailures();
    }

    @Override
    public void failed(PlayerName name) {
        Objects.requireNonNull(name, "name");
        if (failures.incrementAndGet() % SWEEP_EVERY == 0) {
            sweep();
        }
        Settings now = settings.get();
        Instant at = clock.get();
        tallies.compute(
                key(name),
                (key, before) ->
                        before == null || !fresh(before, now) ? new Tally(1, at) : new Tally(before.count() + 1, at));
    }

    @Override
    public void passed(PlayerName name) {
        tallies.compute(key(Objects.requireNonNull(name, "name")), (key, before) -> null);
    }

    /** A fresh code for a new showing to this connection; the code of any earlier showing answers nothing now. */
    public String show(UUID connection) {
        Objects.requireNonNull(connection, "connection");
        Settings now = settings.get();
        StringBuilder code = new StringBuilder(now.length());
        for (int i = 0; i < now.length(); i++) {
            code.append(now.characters().charAt(random.nextInt(now.characters().length())));
        }
        String drawn = code.toString();
        showings.compute(connection, (id, before) -> new Showing(drawn, before != null && before.solved()));
        return drawn;
    }

    /** A fresh code for a new showing, drawn for a map. */
    public CaptchaPicture showPicture(UUID connection) {
        return CaptchaPicture.draw(show(connection), random);
    }

    /** The code of the latest showing to this connection, while it is unanswered. */
    public Optional<String> code(UUID connection) {
        Showing showing = showings.get(Objects.requireNonNull(connection, "connection"));
        return showing == null ? Optional.empty() : Optional.ofNullable(showing.code());
    }

    /** Answers the latest showing, which any answer spends; says whether the answer was its code. */
    public boolean solve(UUID connection, String typed) {
        Objects.requireNonNull(connection, "connection");
        String answer = Objects.requireNonNull(typed, "typed").strip().toUpperCase(Locale.ROOT);
        AtomicBoolean right = new AtomicBoolean();
        showings.computeIfPresent(connection, (id, showing) -> {
            right.set(showing.code() != null && showing.code().equals(answer));
            return new Showing(null, showing.solved() || right.get());
        });
        return right.get();
    }

    /** The connection ended: its code and its solved captcha go with it. */
    public void forget(UUID connection) {
        showings.compute(Objects.requireNonNull(connection, "connection"), (id, before) -> null);
    }

    /** How many names have failures kept, for a test to read. */
    int kept() {
        return tallies.size();
    }

    private boolean solved(UUID connection) {
        Showing showing = showings.get(connection);
        return showing != null && showing.solved();
    }

    private boolean fresh(Tally tally, Settings now) {
        return tally.last().plus(now.failureMemory()).isAfter(clock.get());
    }

    private void sweep() {
        Settings now = settings.get();
        for (String key : List.copyOf(tallies.keySet())) {
            tallies.computeIfPresent(key, (name, kept) -> fresh(kept, now) ? kept : null);
        }
    }

    private static String key(PlayerName name) {
        return name.value().toLowerCase(Locale.ROOT);
    }
}
