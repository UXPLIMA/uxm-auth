package com.uxplima.auth.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.auth.domain.hash.LegacyHashes.Read;
import org.jspecify.annotations.Nullable;

/**
 * One account as another login plugin kept it.
 *
 * @param name the name as the source wrote it, not yet checked against this server's rules
 * @param id the id the source played the account under, which other plugins keep data by
 * @param premiumId the Mojang id the source proved, for a paid account
 * @param password the source's hash as this plugin reads it, or null for an account with none
 * @param totpSecret the authenticator secret in Base32, or null
 */
public record ImportedAccount(
        String name,
        @Nullable UUID id,
        @Nullable UUID premiumId,
        @Nullable Read password,
        @Nullable String totpSecret,
        @Nullable Instant registeredAt,
        @Nullable Instant lastLogin,
        @Nullable String lastAddress) {

    public ImportedAccount {
        Objects.requireNonNull(name, "name");
    }
}
