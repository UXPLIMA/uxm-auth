package com.uxplima.auth.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class AccountTest {

    private static final Instant REGISTERED = Instant.parse("2026-10-17T10:00:00Z");

    private static Account cracked() {
        return new Account(
                UUID.fromString("00000000-0000-3000-8000-000000000001"),
                new PlayerName("Notch"),
                AccountKind.CRACKED,
                null,
                new PasswordHash("$argon2id$stored"),
                REGISTERED,
                "10.0.0.1",
                null,
                null);
    }

    @Test
    @DisplayName("logging in records the time and the address and changes nothing else")
    void loggingInRecordsTimeAndAddress() {
        Instant now = Instant.parse("2026-10-17T11:00:00Z");

        Account after = cracked().loggedIn(now, "1.2.3.4");

        assertThat(after.lastLoginAt()).isEqualTo(now);
        assertThat(after.lastAddress()).isEqualTo("1.2.3.4");
        assertThat(after)
                .usingRecursiveComparison()
                .ignoringFields("lastLoginAt", "lastAddress")
                .isEqualTo(cracked());
    }

    @Test
    @DisplayName("a new password replaces the old one and changes nothing else")
    void aNewPasswordChangesOnlyThePassword() {
        PasswordHash fresh = new PasswordHash("$argon2id$fresh");

        Account after = cracked().withPassword(fresh);

        assertThat(after.password()).isEqualTo(fresh);
        assertThat(after).usingRecursiveComparison().ignoringFields("password").isEqualTo(cracked());
    }

    @Test
    @DisplayName("a hash never prints what it holds")
    void aHashNeverPrints() {
        assertThat(String.valueOf(new PasswordHash("$argon2id$secret"))).doesNotContain("secret");
        assertThat(String.valueOf(cracked())).doesNotContain("stored");
    }
}
