package com.uxplima.auth.application.protection;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.AddressTallies;
import com.uxplima.auth.domain.protection.AddressKey;
import com.uxplima.auth.domain.protection.AddressTally;
import com.uxplima.auth.domain.protection.BanSteps;
import com.uxplima.auth.domain.protection.SharedAddresses;

/**
 * Wrong passwords count against the address they came from, and enough of them ban it for the operator's next
 * step. Through a shared-address filter one player's address is everybody's, so nothing counts there.
 */
public final class AddressBans {

    /** What staff lifting a ban came to. */
    public enum Unban {
        /** The address was banned, and is not now; its count starts again. */
        LIFTED,
        /** The address was not banned; any count it had starts again. */
        NOT_BANNED,
        /** What staff typed is no address. */
        NOT_AN_ADDRESS
    }

    private final AddressTallies tallies;
    private final Supplier<BanSteps> steps;
    private final Supplier<SharedAddresses> shared;
    private final Supplier<Instant> clock;

    public AddressBans(
            AddressTallies tallies,
            Supplier<BanSteps> steps,
            Supplier<SharedAddresses> shared,
            Supplier<Instant> clock) {
        this.tallies = Objects.requireNonNull(tallies, "tallies");
        this.steps = Objects.requireNonNull(steps, "steps");
        this.shared = Objects.requireNonNull(shared, "shared");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * A wrong password from this address through this host: the time the address is banned for now, if it is. An
     * address the server could not read is everybody's whose address it could not read, so it counts nothing.
     */
    public Optional<Duration> failed(String address, String host) {
        Objects.requireNonNull(address, "address");
        if (shared.get().through(address, Objects.requireNonNull(host, "host")) || !AddressKey.isAddress(address)) {
            return Optional.empty();
        }
        Instant now = clock.get();
        BanSteps rules = steps.get();
        AddressTally after = tallies.change(AddressKey.of(address), tally -> tally.failedAt(now, rules));
        return after.bannedAt(now) ? Optional.of(after.leftAt(now)) : Optional.empty();
    }

    /** Staff lift the ban on this address and forget its wrong passwords. */
    public Unban unban(String address) {
        if (!AddressKey.isAddress(Objects.requireNonNull(address, "address"))) {
            return Unban.NOT_AN_ADDRESS;
        }
        String key = AddressKey.of(address);
        boolean banned =
                tallies.find(key).filter(tally -> tally.bannedAt(clock.get())).isPresent();
        tallies.clear(key);
        return banned ? Unban.LIFTED : Unban.NOT_BANNED;
    }

    /** The door's rule: a banned address is refused with the time its ban has left. */
    public Admission.Rule rule() {
        return arrival -> {
            Instant now = clock.get();
            return tallies.find(AddressKey.of(arrival.address()))
                    .filter(tally -> tally.bannedAt(now))
                    .map(tally -> new Admission.Refused(Keys.KICK_BANNED, Map.of(), tally.leftAt(now)));
        };
    }
}
