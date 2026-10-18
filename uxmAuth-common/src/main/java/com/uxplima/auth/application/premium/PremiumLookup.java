package com.uxplima.auth.application.premium;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.uxplima.auth.application.perf.Timings;
import com.uxplima.auth.application.port.NameSource;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.PremiumAnswer;

/**
 * Asks the web whether a name is a paid account's: the sources in the operator's order, the first one that
 * can say deciding, and the answer kept for the time the operator set.
 *
 * <p>An answer nobody could give is not kept, so the next connection asks again. Answers past their time are
 * let go as new ones arrive, so a stream of random names cannot fill the memory.
 */
public final class PremiumLookup {

    /** Past this many kept answers, every new one first lets the expired ones go. */
    private static final int SWEEP_PAST = 256;

    private record Kept(PremiumAnswer answer, Instant until) {}

    private final Supplier<List<NameSource>> sources;
    private final Supplier<Duration> keepFor;
    private final Clock clock;
    private final Timings timings;
    private final Map<String, Kept> kept = new ConcurrentHashMap<>();

    public PremiumLookup(Supplier<List<NameSource>> sources, Supplier<Duration> keepFor, Clock clock) {
        this(sources, keepFor, clock, new Timings(System::nanoTime));
    }

    /** @param timings where the time each source takes to answer goes, as Mojang work */
    public PremiumLookup(Supplier<List<NameSource>> sources, Supplier<Duration> keepFor, Clock clock, Timings timings) {
        this.sources = Objects.requireNonNull(sources, "sources");
        this.keepFor = Objects.requireNonNull(keepFor, "keepFor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timings = Objects.requireNonNull(timings, "timings");
    }

    /** What the web says about this name. It may wait on the network. */
    public PremiumAnswer lookup(PlayerName name) {
        Objects.requireNonNull(name, "name");
        Instant now = clock.instant();
        Kept known = kept.get(name.key());
        if (known != null && now.isBefore(known.until())) {
            return known.answer();
        }
        PremiumAnswer answer = ask(name);
        if (!(answer instanceof PremiumAnswer.Unknown)) {
            keep(name, answer, now);
        }
        return answer;
    }

    private PremiumAnswer ask(PlayerName name) {
        for (NameSource source : sources.get()) {
            long start = timings.start();
            PremiumAnswer answer;
            try {
                answer = source.ask(name);
            } finally {
                timings.stop(Timings.Kind.MOJANG, start);
            }
            if (!(answer instanceof PremiumAnswer.Unknown)) {
                return answer;
            }
        }
        return new PremiumAnswer.Unknown();
    }

    private void keep(PlayerName name, PremiumAnswer answer, Instant now) {
        kept.put(name.key(), new Kept(answer, now.plus(keepFor.get())));
        if (kept.size() > SWEEP_PAST) {
            kept.values().removeIf(old -> !now.isBefore(old.until()));
        }
    }

    /** How many answers are kept, for a test that proves old ones are let go. */
    int kept() {
        return kept.size();
    }
}
