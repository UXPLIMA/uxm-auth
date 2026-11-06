package com.uxplima.auth.application.mail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.uxplima.auth.domain.protection.AddressKey;
import com.uxplima.auth.domain.protection.SharedAddresses;

/**
 * How often one address asks for a recovery code, over every account, in this server's memory. An address that asks
 * for one name after another, to fill mailboxes or to learn which accounts have one, waits once it asked the
 * operator's number inside an hour; the cap of each account is the mail table's, shared by the network. Through a
 * shared-address filter one address is everybody's, so it is not counted there, nor is an address the server could
 * not read.
 */
public final class RecoveryAsks {

    /** How long the asks of one address are counted together. */
    static final Duration HOUR = Duration.ofHours(1);

    /** How often an ask also lets go of the addresses whose hour is over. */
    private static final long SWEEP_EVERY = 1024;

    /** The asks of one address since its hour began. */
    private record Window(Instant start, int asks) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong asked = new AtomicLong();
    private final IntSupplier perHour;
    private final Supplier<SharedAddresses> shared;
    private final Supplier<Instant> clock;

    /** @param perHour how many codes one address may ask inside an hour, 1 or more */
    public RecoveryAsks(IntSupplier perHour, Supplier<SharedAddresses> shared, Supplier<Instant> clock) {
        this.perHour = Objects.requireNonNull(perHour, "perHour");
        this.shared = Objects.requireNonNull(shared, "shared");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Nobody is ever held back, for a part built without a limit. */
    public static RecoveryAsks unlimited() {
        return new RecoveryAsks(() -> Integer.MAX_VALUE, SharedAddresses::none, Instant::now);
    }

    /** Counts an ask from this address through this host: how long it must wait, nothing when it may ask now. */
    public Optional<Duration> ask(String address, String host) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(host, "host");
        if (!AddressKey.isAddress(address) || shared.get().through(address, host)) {
            return Optional.empty();
        }
        if (asked.incrementAndGet() % SWEEP_EVERY == 0) {
            sweep();
        }
        Instant now = clock.get();
        int most = perHour.getAsInt();
        AtomicReference<Duration> wait = new AtomicReference<>(Duration.ZERO);
        windows.compute(AddressKey.of(address), (key, before) -> {
            if (before == null || !now.isBefore(before.start().plus(HOUR))) {
                return new Window(now, 1);
            }
            if (before.asks() >= most) {
                wait.set(Duration.between(now, before.start().plus(HOUR)));
                return before;
            }
            return new Window(before.start(), before.asks() + 1);
        });
        Duration left = Objects.requireNonNull(wait.get(), "wait");
        return left.isZero() ? Optional.empty() : Optional.of(left);
    }

    private void sweep() {
        Instant now = clock.get();
        for (String key : List.copyOf(windows.keySet())) {
            windows.computeIfPresent(
                    key, (address, kept) -> now.isBefore(kept.start().plus(HOUR)) ? kept : null);
        }
    }

    /** Whether a connection from this address, through this host, comes through a shared-address filter. */
    public boolean shared(String address, String host) {
        return shared.get().through(address, host);
    }
}
