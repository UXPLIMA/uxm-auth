package com.uxplima.auth.application.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.uxplima.auth.application.port.BreachedPasswords;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.testing.AuthFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Staff make an account under the name and password rules a player is held to. */
final class ForceRegisterTest {

    private final AuthFixture fixture = new AuthFixture(1);
    private final ForceRegister register = new ForceRegister(
            fixture.accounts,
            fixture.hashing,
            () -> new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of("admin")),
            () -> fixture.policy,
            BreachedPasswords.NONE,
            fixture.clock);

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    @DisplayName("an account is made under the server's offline id, and its password works")
    void anAccountIsMade() {
        ForceRegister.Outcome outcome = register.register("Steve", Password.of("hunter22"), Optional.empty());

        Account made = ((ForceRegister.Outcome.Made) outcome).account();
        assertThat(made.id()).isEqualTo(UuidScheme.offline(new PlayerName("Steve")));
        assertThat(made.kind()).isEqualTo(AccountKind.CRACKED);
        assertThat(fixture.accounts.findByName(new PlayerName("steve"))).contains(made);
        assertThat(fixture.hashing.verify(Password.of("hunter22"), java.util.Objects.requireNonNull(made.password())))
                .isInstanceOf(Verification.Right.class);
    }

    @Test
    @DisplayName("a player online under the name gets the account under the id they play with")
    void anOnlinePlayerKeepsTheirId() {
        UUID online = UUID.randomUUID();

        ForceRegister.Outcome outcome = register.register("Steve", Password.of("hunter22"), Optional.of(online));

        assertThat(((ForceRegister.Outcome.Made) outcome).account().id()).isEqualTo(online);
    }

    @Test
    @DisplayName("a held name, a name the rules refuse and a password the rules refuse make nothing")
    void refusals() {
        fixture.registered("Steve", "hunter22");

        assertThat(register.register("steve", Password.of("hunter22"), Optional.empty()))
                .isEqualTo(new ForceRegister.Outcome.Taken());
        assertThat(register.register("theadmin", Password.of("hunter22"), Optional.empty()))
                .isEqualTo(new ForceRegister.Outcome.NameRefused());
        assertThat(register.register("Alex", Password.of("abc"), Optional.empty()))
                .isInstanceOf(ForceRegister.Outcome.Rejected.class);
        assertThat(fixture.accounts.findByName(new PlayerName("Alex"))).isEmpty();
        assertThat(fixture.accounts.findByName(new PlayerName("theadmin"))).isEmpty();
    }
}
