package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.testing.AuthFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A Bedrock player Floodgate vouched for gets in without a password, and never into an account not theirs. */
final class LetBedrockInTest {

    private final AuthFixture fixture = new AuthFixture(1);
    private final LetBedrockIn useCase =
            new LetBedrockIn(fixture.accounts, () -> AuthFixture.MAX_ATTEMPTS, fixture.clock);

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    @DisplayName("a first visit makes a Bedrock account without a password and lets the player in")
    void aFirstVisitMakesABedrockAccount() {
        UUID connection = UUID.randomUUID();

        AuthState state = useCase.admit(connection, new PlayerName(".Steve"), false, "10.0.0.7");

        assertThat(state).isInstanceOfSatisfying(AuthState.Authenticated.class, in -> {
            assertThat(in.method()).isEqualTo(AuthMethod.BEDROCK);
            assertThat(in.account().kind()).isEqualTo(AccountKind.BEDROCK);
            assertThat(in.account().password()).isNull();
            assertThat(in.account().id()).isEqualTo(connection);
        });
        assertThat(fixture.accounts.findByName(new PlayerName(".Steve"))).isPresent();
    }

    @Test
    @DisplayName("a Bedrock account seen before lets the player in and records the visit")
    void aKnownBedrockAccountLetsIn() {
        UUID connection = UUID.randomUUID();
        useCase.admit(connection, new PlayerName(".Steve"), false, "10.0.0.7");

        AuthState state = useCase.admit(connection, new PlayerName(".Steve"), false, "10.0.0.8");

        assertThat(state).isInstanceOf(AuthState.Authenticated.class);
        assertThat(fixture.accounts
                        .findByName(new PlayerName(".Steve"))
                        .orElseThrow()
                        .lastAddress())
                .isEqualTo("10.0.0.8");
    }

    @Test
    @DisplayName("a player Floodgate linked to a Java account gets into that Java account")
    void aLinkedPlayerGetsIntoTheJavaAccount() {
        Account java = AuthFixture.account("Steve", fixture.hashing.hash(Password.of("hunter22")));
        fixture.accounts.seed(java);

        AuthState state = useCase.admit(java.id(), new PlayerName("Steve"), true, "10.0.0.7");

        assertThat(state).isInstanceOfSatisfying(AuthState.Authenticated.class, in -> {
            assertThat(in.account().id()).isEqualTo(java.id());
            assertThat(in.method()).isEqualTo(AuthMethod.BEDROCK);
        });
    }

    @Test
    @DisplayName("another Xbox identity under a Bedrock account's name is not let into that account")
    void anotherIdentityUnderABedrockNameIsNotLetIn() {
        useCase.admit(UUID.randomUUID(), new PlayerName(".Steve"), false, "10.0.0.7");

        AuthState state = useCase.admit(UUID.randomUUID(), new PlayerName(".Steve"), false, "10.0.0.9");

        assertThat(state).isNotInstanceOf(AuthState.Authenticated.class);
    }

    @Test
    @DisplayName("a linked player is let into the Java account only when Floodgate names that account's id")
    void aLinkedPlayerOnlyReachesTheAccountWithTheirId() {
        Account java = AuthFixture.account("Steve", fixture.hashing.hash(Password.of("hunter22")));
        fixture.accounts.seed(java);

        AuthState state = useCase.admit(UUID.randomUUID(), new PlayerName("Steve"), true, "10.0.0.7");

        assertThat(state).isInstanceOf(AuthState.Registered.class);
    }

    @Test
    @DisplayName("an unlinked Bedrock player under a Java account's name is asked for that account's password")
    void anUnlinkedPlayerUnderAJavaNameIsAskedForThePassword() {
        Account java = AuthFixture.account("Steve", fixture.hashing.hash(Password.of("hunter22")));
        fixture.accounts.seed(java);

        AuthState state = useCase.admit(UUID.randomUUID(), new PlayerName("Steve"), false, "10.0.0.7");

        assertThat(state).isInstanceOf(AuthState.Registered.class);
    }
}
