package com.uxplima.auth.application.protection;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.port.AccountTallies;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.protection.AddressKey;
import com.uxplima.auth.domain.protection.AddressTally;
import com.uxplima.auth.domain.protection.BanSteps;
import com.uxplima.auth.domain.protection.SharedAddresses;

/**
 * Wrong passwords and codes count against the account they were typed for, from any address and through any
 * filter, and enough of them lock it for the operator's next step. The address bans count per address, so a
 * guesser behind a shared filter, or one with a fresh address for every connection, was never stopped by them;
 * this count follows the account wherever the guesses come from.
 *
 * <p>A try is counted as wrong before it is checked, so tries sent together on many connections cannot pass the
 * limit, and a right one gives its count back. A lock is short and grows step by step, never for good, and an
 * account quiet for the operator's time starts again from the first step. Only a password or a code is ever held
 * back by it: a premium or Bedrock login and a remembered session pass as before, and the owner back on the
 * address they last logged in from, when no filter shares it, is not held back either; their guesses still count.
 * So a stranger who types wrong passwords on purpose delays the owner a little, and only where the owner also
 * types a password from somewhere new.
 */
public final class AccountLockout implements LogIn.Lockout {

    private final AccountTallies tallies;
    private final Supplier<BanSteps> steps;
    private final Supplier<SharedAddresses> shared;
    private final Supplier<Instant> clock;

    /** @param steps how many wrong tries lock an account, for how long, and when it is forgiven */
    public AccountLockout(
            AccountTallies tallies,
            Supplier<BanSteps> steps,
            Supplier<SharedAddresses> shared,
            Supplier<Instant> clock) {
        this.tallies = Objects.requireNonNull(tallies, "tallies");
        this.steps = Objects.requireNonNull(steps, "steps");
        this.shared = Objects.requireNonNull(shared, "shared");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Try take(Account account, String address, String host) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(host, "host");
        return take(account.id(), !ownersAddress(account, address, host));
    }

    @Override
    public Try take(UUID account) {
        return take(Objects.requireNonNull(account, "account"), true);
    }

    private Try take(UUID account, boolean held) {
        // The table keeps milliseconds, so the moment is cut to them: a try given back finds its tally as written.
        Instant now = clock.get().truncatedTo(ChronoUnit.MILLIS);
        if (held) {
            Optional<AddressTally> before = tallies.find(account).filter(tally -> tally.bannedAt(now));
            if (before.isPresent()) {
                return new Try.Refused(before.get().leftAt(now));
            }
        }
        BanSteps rules = steps.get();
        AtomicReference<AddressTally> before = new AtomicReference<>();
        AddressTally after = tallies.change(account, tally -> {
            before.set(tally);
            return held && tally.bannedAt(now) ? tally : tally.failedAt(now, rules);
        });
        AddressTally was = Objects.requireNonNull(before.get(), "before");
        if (after.equals(was) && was.bannedAt(now)) {
            return new Try.Refused(was.leftAt(now));
        }
        return new Try.Taken(account, was, after, now);
    }

    @Override
    public void right(Try.Taken taken) {
        Objects.requireNonNull(taken, "taken");
        // Only the tally this try left is put back; one another try changed since keeps that try's count.
        tallies.change(taken.account(), now -> now.equals(taken.after()) ? taken.before() : now);
    }

    @Override
    public void passed(UUID account) {
        tallies.clear(Objects.requireNonNull(account, "account"));
    }

    /** Whether the address is the one the account last logged in from, its /64 for IPv6, with no filter sharing it. */
    private boolean ownersAddress(Account account, String address, String host) {
        String last = account.lastAddress();
        return last != null
                && AddressKey.isAddress(address)
                && !shared.get().through(address, host)
                && AddressKey.of(address).equals(AddressKey.of(last));
    }
}
