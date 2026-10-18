package com.uxplima.auth.application.protection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.protection.AddressRanges;
import com.uxplima.auth.domain.protection.SharedAddresses;
import com.uxplima.auth.testing.InMemoryAccounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** One address may make so many password accounts inside the window, unless the operator exempts it. */
final class AccountCapTest {

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");
    private static final String HOME = "203.0.113.7";
    private static final String FILTER = "198.51.100.20";
    private static final String PLAY = "play.example.org";

    private final InMemoryAccounts accounts = new InMemoryAccounts();

    private AccountCap cap() {
        return new AccountCap(
                accounts,
                () -> new AccountCap.Settings(2, Duration.ofHours(24), new AddressRanges(List.of("10.0.0.0/8"))),
                () -> new SharedAddresses(new HostPatterns(List.of()), new AddressRanges(List.of("198.51.100.0/24"))),
                () -> NOW);
    }

    private void made(String name, String address, Instant at) {
        accounts.insert(new Account(
                UUID.randomUUID(),
                new PlayerName(name),
                AccountKind.CRACKED,
                null,
                new PasswordHash("$argon2id$v=19$m=1024,t=1,p=1$c29tZXNhbHQxMjM0NTY3OA$aGFzaA"),
                at,
                address,
                null,
                null));
    }

    @Test
    @DisplayName("below the limit an address may make one more account, at the limit it may not")
    void theLimit() {
        made("Alex", HOME, NOW.minusSeconds(60));
        assertThat(cap().full(HOME, PLAY)).isFalse();

        made("Steve", HOME, NOW.minusSeconds(30));
        assertThat(cap().full(HOME, PLAY)).isTrue();
        assertThat(cap().full("203.0.113.8", PLAY)).isFalse();
    }

    @Test
    @DisplayName("an account made before the window does not count")
    void theWindow() {
        made("Alex", HOME, NOW.minus(Duration.ofHours(25)));
        made("Steve", HOME, NOW.minusSeconds(30));

        assertThat(cap().full(HOME, PLAY)).isFalse();
    }

    @Test
    @DisplayName("an exempt address, a shared-address filter and an address the server could not read are never full")
    void theExemptions() {
        for (String address : List.of("10.1.1.1", FILTER, "unknown")) {
            made("A" + address.hashCode(), address, NOW);
            made("B" + address.hashCode(), address, NOW);
            made("C" + address.hashCode(), address, NOW);

            assertThat(cap().full(address, PLAY)).as(address).isFalse();
        }
    }
}
