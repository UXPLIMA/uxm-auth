package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.auth.api.AccountType;
import com.uxplima.auth.api.LoginWay;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.testing.AuthFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Another plugin asks whether a player is in and how, answered from the sessions in memory. */
final class SessionsAuthApiTest {

    private final Sessions sessions = new Sessions();
    private final SessionsAuthApi api = new SessionsAuthApi(sessions::state);

    private static Account premium() {
        Account cracked = AuthFixture.account("Notch", new com.uxplima.auth.domain.hash.PasswordHash("x"));
        return new Account(
                cracked.id(),
                cracked.name(),
                AccountKind.PREMIUM,
                UUID.randomUUID(),
                null,
                cracked.registeredAt(),
                cracked.registrationAddress(),
                cracked.lastLoginAt(),
                cracked.lastAddress());
    }

    @Test
    @DisplayName("a logged-in player is authenticated, with the way they came in and their kind")
    void aLoggedInPlayer() {
        UUID id = UUID.randomUUID();
        sessions.open(id, new AuthState.Authenticated(premium(), AuthMethod.PREMIUM));

        assertThat(api.isAuthenticated(id)).isTrue();
        assertThat(api.method(id)).contains(LoginWay.PREMIUM);
        assertThat(api.kind(id)).contains(AccountType.PREMIUM);
    }

    @Test
    @DisplayName("a player still waiting, and one who never joined, are not authenticated and answer nothing")
    void waitingAndAbsentPlayers() {
        UUID waiting = UUID.randomUUID();
        sessions.open(waiting, new AuthState.Registered(premium(), Attempts.fresh(3)));
        UUID absent = UUID.randomUUID();

        for (UUID id : new UUID[] {waiting, absent}) {
            assertThat(api.isAuthenticated(id)).isFalse();
            assertThat(api.method(id)).isEmpty();
            assertThat(api.kind(id)).isEmpty();
        }
    }

    @Test
    @DisplayName("every way in and every kind has its name in the API")
    void everyNameMaps() {
        for (AuthMethod method : AuthMethod.values()) {
            assertThat(SessionsAuthApi.way(method).name()).isEqualTo(method.name());
        }
        for (AccountKind kind : AccountKind.values()) {
            assertThat(SessionsAuthApi.type(kind).name()).isEqualTo(kind.name());
        }
    }
}
