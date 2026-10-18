package com.uxplima.auth.velocity.adapter.inbound;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.api.AccountType;
import com.uxplima.auth.api.AuthApi;
import com.uxplima.auth.api.LoginWay;
import com.uxplima.auth.application.SessionsAuthApi;
import com.uxplima.auth.domain.state.AuthMethod;

/**
 * The API another proxy plugin asks, answered from the proxy's own record of who is in. The proxy keeps the way a
 * player got in, not their account, so the kind is the one the way proves: a password or a registration is a
 * cracked account, Mojang's word a premium one, Floodgate's a Bedrock one; a remembered session, staff, or a
 * server's report proves none, and the kind answers empty.
 */
public final class ProxyAuthApi implements AuthApi {

    private final ProxySessions sessions;

    public ProxyAuthApi(ProxySessions sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @Override
    public boolean isAuthenticated(UUID player) {
        return sessions.way(Objects.requireNonNull(player, "player")).isPresent();
    }

    @Override
    public Optional<LoginWay> method(UUID player) {
        return sessions.way(Objects.requireNonNull(player, "player")).map(SessionsAuthApi::way);
    }

    @Override
    public Optional<AccountType> kind(UUID player) {
        return sessions.way(Objects.requireNonNull(player, "player")).flatMap(ProxyAuthApi::proven);
    }

    private static Optional<AccountType> proven(AuthMethod way) {
        return switch (way) {
            case PASSWORD, REGISTERED -> Optional.of(AccountType.CRACKED);
            case PREMIUM -> Optional.of(AccountType.PREMIUM);
            case BEDROCK -> Optional.of(AccountType.BEDROCK);
            case SESSION, FORCED, NETWORK -> Optional.empty();
        };
    }
}
