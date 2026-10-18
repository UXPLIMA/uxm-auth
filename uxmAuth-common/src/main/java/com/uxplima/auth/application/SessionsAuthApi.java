package com.uxplima.auth.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.uxplima.auth.api.AccountType;
import com.uxplima.auth.api.AuthApi;
import com.uxplima.auth.api.LoginWay;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;

/** The API another plugin asks, answered from the sessions in memory, on a server and on a proxy alike. */
public final class SessionsAuthApi implements AuthApi {

    private final Function<UUID, Optional<AuthState>> sessions;

    /** @param sessions the state of a player's session, read from memory */
    public SessionsAuthApi(Function<UUID, Optional<AuthState>> sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    private Optional<AuthState.Authenticated> in(UUID player) {
        return sessions.apply(Objects.requireNonNull(player, "player"))
                .filter(AuthState.Authenticated.class::isInstance)
                .map(AuthState.Authenticated.class::cast);
    }

    @Override
    public boolean isAuthenticated(UUID player) {
        return in(player).isPresent();
    }

    @Override
    public Optional<LoginWay> method(UUID player) {
        return in(player).map(found -> way(found.method()));
    }

    @Override
    public Optional<AccountType> kind(UUID player) {
        return in(player).map(found -> type(found.account().kind()));
    }

    /** The API's name of a way in. */
    public static LoginWay way(AuthMethod method) {
        return switch (method) {
            case PASSWORD -> LoginWay.PASSWORD;
            case REGISTERED -> LoginWay.REGISTERED;
            case PREMIUM -> LoginWay.PREMIUM;
            case BEDROCK -> LoginWay.BEDROCK;
            case SESSION -> LoginWay.SESSION;
            case FORCED -> LoginWay.FORCED;
            case NETWORK -> LoginWay.NETWORK;
        };
    }

    /** The API's name of a kind of account. */
    public static AccountType type(AccountKind kind) {
        return switch (kind) {
            case CRACKED -> AccountType.CRACKED;
            case PREMIUM -> AccountType.PREMIUM;
            case BEDROCK -> AccountType.BEDROCK;
        };
    }
}
