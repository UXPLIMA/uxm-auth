package com.uxplima.auth.application.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.InMemoryEmails;
import com.uxplima.auth.testing.InMemoryFactors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Staff turn an account's second factor off: the app, the backup codes and the mailed code all go. */
final class FactorOffTest {

    private final AuthFixture fixture = new AuthFixture(1);
    private final InMemoryFactors factors = new InMemoryFactors();
    private final InMemoryEmails emails = new InMemoryEmails();
    private final FactorOff off = new FactorOff(fixture.accounts, factors, emails);

    @AfterEach
    void close() {
        fixture.close();
    }

    private UUID steve() {
        fixture.registered("Steve", "hunter22");
        return fixture.accounts
                .findByName(new PlayerName("Steve"))
                .orElseThrow()
                .id();
    }

    @Test
    @DisplayName("every second factor of the account goes")
    void everyFactorGoes() {
        UUID steve = steve();
        factors.setApp(steve, new TotpSecret(new byte[20]));
        factors.setBackupCodes(steve, List.of(new PasswordHash("$x$a")));
        emails.setAddress(
                steve,
                com.uxplima.auth.domain.mail.EmailAddress.parse("steve@example.net")
                        .orElseThrow());
        emails.setMailsLoginCode(steve, true);
        assertThat(emails.mailsLoginCode(steve)).as("the mailed code was on").isTrue();

        assertThat(off.off(new PlayerName("steve"))).isEqualTo(FactorOff.Off.OFF);

        assertThat(factors.app(steve)).isEmpty();
        assertThat(factors.backupCodes(steve)).isEmpty();
        assertThat(emails.mailsLoginCode(steve)).isFalse();
    }

    @Test
    @DisplayName("an account with no second factor says so, and an unknown name says so")
    void nothingToTurnOff() {
        steve();

        assertThat(off.off(new PlayerName("Steve"))).isEqualTo(FactorOff.Off.NONE);
        assertThat(off.off(new PlayerName("Nobody"))).isEqualTo(FactorOff.Off.UNKNOWN);
    }
}
