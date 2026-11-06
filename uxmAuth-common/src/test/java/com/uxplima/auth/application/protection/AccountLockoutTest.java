package com.uxplima.auth.application.protection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.port.AccountTallies;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.protection.AddressRanges;
import com.uxplima.auth.domain.protection.AddressTally;
import com.uxplima.auth.domain.protection.BanSteps;
import com.uxplima.auth.domain.protection.SharedAddresses;
import org.junit.jupiter.api.Test;

class AccountLockoutTest {

    private static final Instant START = Instant.parse("2030-01-01T12:00:00Z");
    private static final String HOME = "203.0.113.7";
    private static final String FILTER = "198.51.100.7";
    private static final BanSteps STEPS =
            new BanSteps(List.of(Duration.ofMinutes(1), Duration.ofMinutes(5)), 3, Duration.ofHours(24));

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final MemoryTallies tallies = new MemoryTallies();
    private final SharedAddresses shared =
            new SharedAddresses(new HostPatterns(List.of()), new AddressRanges(List.of("198.51.100.0/24")));
    private final AccountLockout lockout = new AccountLockout(tallies, () -> STEPS, () -> shared, now::get);
    private final Account steve = new Account(
            UUID.randomUUID(),
            new PlayerName("Steve"),
            AccountKind.CRACKED,
            null,
            null,
            START.minus(Duration.ofDays(3)),
            HOME,
            START.minus(Duration.ofDays(1)),
            HOME);

    @Test
    void enoughWrongTriesFromAnywhereLockTheAccountForAWhile() {
        wrong("192.0.2.1");
        wrong("192.0.2.2");
        LogIn.Lockout.Try third = lockout.take(steve, "192.0.2.3", "");

        assertThat(((LogIn.Lockout.Try.Taken) third).locks()).contains(Duration.ofMinutes(1));
        assertThat(lockout.take(steve, "192.0.2.4", ""))
                .isEqualTo(new LogIn.Lockout.Try.Refused(Duration.ofMinutes(1)));

        now.set(START.plus(Duration.ofMinutes(1)));
        assertThat(lockout.take(steve, "192.0.2.4", "")).isInstanceOf(LogIn.Lockout.Try.Taken.class);
    }

    @Test
    void aFilterSharesNoExemptionButTheOwnersOwnAddressStillTries() {
        wrong(FILTER);
        wrong(FILTER);
        wrong(FILTER);

        assertThat(lockout.take(steve, FILTER, "play.example.com")).isInstanceOf(LogIn.Lockout.Try.Refused.class);
        assertThat(lockout.take(steve, HOME, "")).isInstanceOf(LogIn.Lockout.Try.Taken.class);
        assertThat(lockout.take(steve.id())).isInstanceOf(LogIn.Lockout.Try.Refused.class);
    }

    @Test
    void triesSentTogetherCountBeforeTheyAreChecked() {
        LogIn.Lockout.Try first = lockout.take(steve, "192.0.2.1", "");
        LogIn.Lockout.Try second = lockout.take(steve, "192.0.2.2", "");
        LogIn.Lockout.Try third = lockout.take(steve, "192.0.2.3", "");

        assertThat(first).isInstanceOf(LogIn.Lockout.Try.Taken.class);
        assertThat(second).isInstanceOf(LogIn.Lockout.Try.Taken.class);
        assertThat(((LogIn.Lockout.Try.Taken) third).locks()).isPresent();
        assertThat(lockout.take(steve, "192.0.2.4", "")).isInstanceOf(LogIn.Lockout.Try.Refused.class);
    }

    @Test
    void aRightTryGivesItsCountBackAndALoginStartsAgain() {
        wrong("192.0.2.1");
        lockout.right((LogIn.Lockout.Try.Taken) lockout.take(steve, "192.0.2.2", ""));
        wrong("192.0.2.3");

        assertThat(lockout.take(steve, "192.0.2.4", ""))
                .isInstanceOfSatisfying(LogIn.Lockout.Try.Taken.class, taken -> {
                    assertThat(taken.locks()).isPresent();
                });

        lockout.passed(steve.id());
        assertThat(tallies.find(steve.id())).isEmpty();
    }

    private void wrong(String address) {
        assertThat(lockout.take(steve, address, "")).isInstanceOf(LogIn.Lockout.Try.Taken.class);
    }

    /** The tallies in a map. */
    static final class MemoryTallies implements AccountTallies {
        private final Map<UUID, AddressTally> kept = new ConcurrentHashMap<>();

        @Override
        public Optional<AddressTally> find(UUID account) {
            return Optional.ofNullable(kept.get(account));
        }

        @Override
        public AddressTally change(UUID account, UnaryOperator<AddressTally> change) {
            return kept.compute(account, (id, before) -> change.apply(before == null ? AddressTally.clean() : before));
        }

        @Override
        public void clear(UUID account) {
            kept.remove(account);
        }
    }
}
