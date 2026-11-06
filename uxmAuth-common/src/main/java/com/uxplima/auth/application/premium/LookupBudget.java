package com.uxplima.auth.application.premium;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;

import com.uxplima.auth.domain.protection.AddressKey;

/**
 * How many names one address may have the web asked about in ten minutes. Mojang's cap is shared by every player,
 * so an address that sends name after new name would otherwise spend it for everybody, and a name nobody can look
 * up is one the door cannot be sure of. A name the web answered lately costs nothing, so a player who comes back
 * pays once. An IPv6 address counts with its /64, as the other address rules count it.
 */
public final class LookupBudget {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    /** Past this many addresses, every new request first lets the ones that went quiet go. */
    private static final int SWEEP_PAST = 256;

    private final IntSupplier perAddress;
    private final Clock clock;
    private final Map<String, Deque<Instant>> spent = new HashMap<>();

    /** @param perAddress the lookups one address may cause in ten minutes; 0 or less for no limit */
    public LookupBudget(IntSupplier perAddress, Clock clock) {
        this.perAddress = Objects.requireNonNull(perAddress, "perAddress");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** No limit: every address may have the web asked as often as it likes. */
    public static LookupBudget unlimited() {
        return new LookupBudget(() -> 0, Clock.systemUTC());
    }

    /** Counts one lookup for this address and says yes, or says no while its last ten minutes are full. */
    public synchronized boolean take(String address) {
        Objects.requireNonNull(address, "address");
        int cap = perAddress.getAsInt();
        if (cap <= 0) {
            return true;
        }
        Instant now = clock.instant();
        Instant oldest = now.minus(WINDOW);
        if (spent.size() > SWEEP_PAST) {
            spent.values()
                    .removeIf(times -> times.isEmpty() || !times.peekLast().isAfter(oldest));
        }
        Deque<Instant> times = spent.computeIfAbsent(AddressKey.of(address), key -> new ArrayDeque<>());
        while (!times.isEmpty() && !times.peekFirst().isAfter(oldest)) {
            times.pollFirst();
        }
        if (times.size() >= cap) {
            return false;
        }
        times.addLast(now);
        return true;
    }
}
