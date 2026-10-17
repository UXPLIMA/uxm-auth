package com.uxplima.auth.application.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.premium.Decision;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.premium.NameOwnership;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.WhenUnknown;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.TurnedClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A connection at the hello, before the server makes a profile for it: is it asked for the handshake? */
final class AtTheDoorTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final String HERE = "10.0.0.7";
    private static final String HOST = "play.example.org";

    private final AuthFixture fixture = new AuthFixture(1);
    private final TurnedClock clock = new TurnedClock(AuthFixture.NOW);
    private final AtomicInteger asked = new AtomicInteger();
    private PremiumAnswer web = new PremiumAnswer.Premium(NOTCH, "Notch");
    private final NameMemory failed = new NameMemory(() -> Duration.ofMinutes(5), clock);
    private final NameMemory claims = new NameMemory(() -> Duration.ofMinutes(10), clock);
    private final AtTheDoor door = new AtTheDoor(
            fixture.accounts,
            () -> new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of()),
            new PremiumLookup(
                    () -> List.of(name -> {
                        asked.incrementAndGet();
                        return web;
                    }),
                    () -> Duration.ofMinutes(30),
                    clock),
            () -> new NameOwnership(
                    NameRule.AUTOMATIC, WhenUnknown.CRACKED, new HostPatterns(List.of()), new HostPatterns(List.of())),
            failed,
            claims);

    @AfterEach
    void close() {
        fixture.close();
    }

    private Account held(String name, AccountKind kind) {
        Account account = new Account(
                UUID.randomUUID(),
                new PlayerName(name),
                kind,
                null,
                kind == AccountKind.CRACKED ? fixture.hashing.hash(Password.of("hunter22")) : null,
                AuthFixture.NOW,
                "10.0.0.1",
                null,
                null);
        fixture.accounts.seed(account);
        return account;
    }

    @Test
    @DisplayName("a new name Mojang knows is asked for the handshake")
    void aNewPaidNameIsAsked() {
        assertThat(door.decide("Notch", HERE, HOST)).isEqualTo(new Decision.Handshake());
    }

    @Test
    @DisplayName("a premium account is asked for the handshake without asking the web")
    void aPremiumAccountIsAsked() {
        held("Notch", AccountKind.PREMIUM);

        assertThat(door.decide("Notch", HERE, HOST)).isEqualTo(new Decision.Handshake());
        assertThat(asked).hasValue(0);
    }

    @Test
    @DisplayName("a cracked account keeps its name under the automatic rule, and the web is not asked")
    void aCrackedAccountKeepsItsName() {
        held("Notch", AccountKind.CRACKED);

        assertThat(door.decide("Notch", HERE, HOST)).isEqualTo(new Decision.Cracked(false, false));
        assertThat(asked).hasValue(0);
    }

    @Test
    @DisplayName("an account taken while nobody could say who owns the name is asked about again")
    void anUnverifiedAccountIsAskedAgain() {
        fixture.accounts.seed(held("Notch", AccountKind.CRACKED).asUnverified());

        assertThat(door.decide("Notch", HERE, HOST)).isEqualTo(new Decision.Handshake());
        assertThat(asked).hasValue(1);
    }

    @Test
    @DisplayName("a claim asks the next connection from its own address for the handshake, once")
    void aClaimAsksOnceFromItsAddress() {
        held("Notch", AccountKind.CRACKED);
        door.claim(HERE, new PlayerName("Notch"));

        assertThat(door.decide("Notch", "10.0.0.8", HOST)).isEqualTo(new Decision.Cracked(false, false));
        assertThat(door.decide("Notch", HERE, HOST)).isEqualTo(new Decision.Handshake());
        assertThat(door.decide("Notch", HERE, HOST)).isEqualTo(new Decision.Cracked(false, false));
    }

    @Test
    @DisplayName("a failed handshake is remembered, and a passed one forgets it")
    void aFailureIsRememberedUntilAPass() {
        door.failed(HERE, new PlayerName("Notch"));
        assertThat(door.decide("Notch", HERE, HOST)).isEqualTo(new Decision.Cracked(false, true));

        door.passed(HERE, new PlayerName("Notch"));
        assertThat(door.decide("Notch", HERE, HOST)).isEqualTo(new Decision.Handshake());
    }

    @Test
    @DisplayName("a name the rules refuse, or a registered name in another case, is left to the name check unasked")
    void aNameTheDoorRefusesIsNotAsked() {
        held("Notch", AccountKind.PREMIUM);

        assertThat(door.decide("ab", HERE, HOST)).isEqualTo(new Decision.Cracked(false, false));
        assertThat(door.decide("Ste-ve", HERE, HOST)).isEqualTo(new Decision.Cracked(false, false));
        assertThat(door.decide("NOTCH", HERE, HOST)).isEqualTo(new Decision.Cracked(false, false));
        assertThat(asked).hasValue(0);
    }
}
