package com.uxplima.auth.domain.account;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import org.jspecify.annotations.Nullable;

/**
 * One account: who it is, how it proves it, and when and where it was last used.
 *
 * <p>The password is absent for an account that never had one, a premium or Bedrock player who was let in
 * by the handshake or by Xbox Live. The premium id is the Mojang UUID once the account has proved one.
 */
public record Account(
        UUID id,
        PlayerName name,
        AccountKind kind,
        @Nullable UUID premiumId,
        @Nullable PasswordHash password,
        Instant registeredAt,
        String registrationAddress,
        @Nullable Instant lastLoginAt,
        @Nullable String lastAddress) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(registeredAt, "registeredAt");
        Objects.requireNonNull(registrationAddress, "registrationAddress");
    }

    /** The same account with a new password, as after a change or a rehash. */
    public Account withPassword(PasswordHash fresh) {
        Objects.requireNonNull(fresh, "fresh");
        return new Account(
                id, name, kind, premiumId, fresh, registeredAt, registrationAddress, lastLoginAt, lastAddress);
    }

    /** The same account after a login at this moment from this address. */
    public Account loggedIn(Instant at, String address) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(address, "address");
        return new Account(id, name, kind, premiumId, password, registeredAt, registrationAddress, at, address);
    }
}
