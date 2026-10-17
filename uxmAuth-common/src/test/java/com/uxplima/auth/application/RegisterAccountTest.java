package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.testing.AuthFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
final class RegisterAccountTest {

    private AuthFixture fixture = new AuthFixture(1);

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    @DisplayName("two different passwords register nothing")
    void registerRefusesAMismatch() {
        UUID connection = fixture.unregistered("Steve");

        RegisterResult result = register(connection, "Steve", "hunter22", "hunter23");

        assertThat(result).isEqualTo(new RegisterResult.Mismatch());
        assertThat(fixture.accounts.writes()).isZero();
    }

    @Test
    @DisplayName("a password the policy refuses is refused with the reason")
    void registerRefusesAWeakPassword() {
        UUID connection = fixture.unregistered("Steve");

        RegisterResult result = register(connection, "Steve", "abc", "abc");

        assertThat(result).isEqualTo(new RegisterResult.Rejected(new PasswordVerdict.TooShort(6)));
    }

    @Test
    @DisplayName("a name somebody holds in another case is refused, naming them as they wrote it")
    void registerRefusesATakenNameInItsStoredCase() {
        fixture.registered("Notch", "original1");
        UUID connection = fixture.unregistered("notch");

        RegisterResult result = register(connection, "notch", "hunter22", "hunter22");

        assertThat(result).isEqualTo(new RegisterResult.NameTaken(new PlayerName("Notch")));
        assertThat(fixture.sessions.state(connection)).containsInstanceOf(AuthState.Unregistered.class);
    }

    @Test
    @DisplayName("a new account leaves the player logged in, by registering")
    void registerLeavesThePlayerAuthenticatedAsRegistered() {
        UUID connection = fixture.unregistered("Steve");

        RegisterResult result = register(connection, "Steve", "hunter22", "hunter22");

        assertThat(result).isInstanceOf(RegisterResult.Created.class);
        assertThat(fixture.sessions.state(connection))
                .hasValueSatisfying(state -> assertThat(state)
                        .isInstanceOfSatisfying(AuthState.Authenticated.class, logged -> {
                            assertThat(logged.method()).isEqualTo(AuthMethod.REGISTERED);
                            assertThat(logged.account().registrationAddress()).isEqualTo("10.0.0.7");
                        }));
        assertThat(fixture.accounts.findByName(new PlayerName("steve"))).isPresent();
    }

    @Test
    @DisplayName("a registration while no premium source could say who owns the name makes an unverified account")
    void aRegistrationNobodyCouldLookUpIsUnverified() {
        UUID connection = UUID.randomUUID();
        fixture.sessions.open(connection, new AuthState.Unregistered(new PlayerName("Steve"), true));

        register(connection, "Steve", "hunter22", "hunter22");

        assertThat(fixture.accounts.findByName(new PlayerName("Steve")))
                .hasValueSatisfying(account -> assertThat(account.unverified()).isTrue());
    }

    @Test
    @DisplayName("an ordinary registration makes an account nobody doubts")
    void anOrdinaryRegistrationIsVerified() {
        UUID connection = fixture.unregistered("Steve");

        register(connection, "Steve", "hunter22", "hunter22");

        assertThat(fixture.accounts.findByName(new PlayerName("Steve")))
                .hasValueSatisfying(account -> assertThat(account.unverified()).isFalse());
    }

    @Test
    @DisplayName("a player who already has an account cannot register again")
    void registerRefusesAnAccountHolder() {
        UUID connection = fixture.registered("Steve", "hunter22");

        assertThat(register(connection, "Steve", "other222", "other222"))
                .isEqualTo(new RegisterResult.AlreadyRegistered());
    }

    @Test
    @DisplayName("the passwords are wiped whatever the answer")
    void passwordsAreWiped() {
        UUID connection = fixture.unregistered("Steve");
        Password password = Password.of("hunter22");
        Password confirm = Password.of("hunter23");

        useCase()
                .run(connection, new PlayerName("Steve"), password, confirm, "10.0.0.7")
                .join();

        assertThat(password.isWiped()).isTrue();
        assertThat(confirm.isWiped()).isTrue();
    }

    @Test
    @DisplayName("two registrations of one name at once end in one account")
    void twoRegistrationsOfOneNameRaceToOneAccount() {
        fixture.close();
        fixture = new AuthFixture(2);
        UUID first = fixture.unregistered("Notch");
        UUID second = fixture.unregistered("notch");
        RegisterAccount register = useCase();

        CompletableFuture<RegisterResult> one = register.run(
                first, new PlayerName("Notch"), Password.of("hunter22"), Password.of("hunter22"), "10.0.0.1");
        CompletableFuture<RegisterResult> two = register.run(
                second, new PlayerName("notch"), Password.of("hunter33"), Password.of("hunter33"), "10.0.0.2");
        List<RegisterResult> results = List.of(one.join(), two.join());

        RegisterResult.Created created = results.stream()
                .filter(RegisterResult.Created.class::isInstance)
                .map(RegisterResult.Created.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(results).containsOnlyOnce(created);
        assertThat(results)
                .contains(new RegisterResult.NameTaken(created.account().name()));
    }

    @Test
    @DisplayName("a registration bound to an opening another connection replaced is gone, and makes nothing")
    void aRegistrationForAnEarlierOpeningIsGone() {
        UUID connection = fixture.unregistered("Steve");
        long first = fixture.sessions.held(connection).orElseThrow().generation();
        fixture.sessions.open(connection, fixture.sessions.state(connection).orElseThrow());

        RegisterResult result = useCase()
                .run(
                        connection,
                        first,
                        new PlayerName("Steve"),
                        Password.of("hunter22"),
                        Password.of("hunter22"),
                        "10.0.0.7")
                .join();

        assertThat(result).isEqualTo(new RegisterResult.Gone());
        assertThat(fixture.accounts.findByName(new PlayerName("Steve"))).isEmpty();
    }

    private RegisterResult register(UUID connection, String name, String password, String confirm) {
        return useCase()
                .run(connection, new PlayerName(name), Password.of(password), Password.of(confirm), "10.0.0.7")
                .join();
    }

    private RegisterAccount useCase() {
        return new RegisterAccount(
                fixture.accounts, fixture.hashing, fixture.lane, fixture.sessions, () -> fixture.policy, fixture.clock);
    }
}
