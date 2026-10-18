package com.uxplima.auth.application.factor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.testing.InMemoryEmails;
import com.uxplima.auth.testing.InMemoryFactors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Whether an account has a second factor, known in memory from its login and followed through every change. */
final class FactorStatesTest {

    private final InMemoryFactors factorTable = new InMemoryFactors();
    private final InMemoryEmails emailTable = new InMemoryEmails();
    private final FactorStates states = new FactorStates(factorTable, emailTable);
    private final Factors factors = states.watching(factorTable);
    private final Emails emails = states.watching(emailTable);
    private final UUID steve = UUID.randomUUID();

    @Test
    @DisplayName("nothing is known before the login reads it, and the login reads app and mail alike")
    void theLoginReadsIt() {
        assertThat(states.on(steve)).isEmpty();
        states.refresh(steve);
        assertThat(states.on(steve)).contains(false);

        emailTable.setAddress(steve, EmailAddress.parse("steve@example.net").orElseThrow());
        emailTable.setMailsLoginCode(steve, true);
        states.refresh(steve);
        assertThat(states.on(steve)).contains(true);
    }

    @Test
    @DisplayName("setting up an app, turning it off, and the mailed code each change what is known")
    void changesAreFollowed() {
        states.refresh(steve);

        factors.setApp(steve, new TotpSecret(new byte[20]));
        assertThat(states.on(steve)).contains(true);
        factors.removeApp(steve);
        assertThat(states.on(steve)).contains(false);
        emails.setAddress(steve, EmailAddress.parse("steve@example.net").orElseThrow());
        emails.setMailsLoginCode(steve, true);
        assertThat(states.on(steve)).contains(true);
        emails.removeAddress(steve);
        assertThat(states.on(steve))
                .as("the table answers whether a code is mailed without an address")
                .contains(emailTable.mailsLoginCode(steve));
    }

    @Test
    @DisplayName("a change to an account nobody here logged in to is not kept, and a player who left is forgotten")
    void onlyThePlayersHereAreKept() {
        UUID away = UUID.randomUUID();
        factors.setApp(away, new TotpSecret(new byte[20]));
        assertThat(states.on(away)).isEmpty();

        states.refresh(steve);
        states.forget(steve);
        assertThat(states.on(steve)).isEmpty();
    }

    @Test
    @DisplayName("what the watched tables answer is the tables' own")
    void theTablesAnswer() {
        factors.setApp(steve, new TotpSecret(new byte[20]));
        assertThat(factors.app(steve)).isEqualTo(factorTable.app(steve));
        assertThat(factors.useStep(steve, 4)).isTrue();
        assertThat(factors.backupCodes(steve)).isEqualTo(factorTable.backupCodes(steve));
        emails.setAddress(steve, EmailAddress.parse("steve@example.net").orElseThrow());
        assertThat(emails.address(steve)).isEqualTo(emailTable.address(steve));
        assertThat(emails.mailsLoginCode(steve)).isEqualTo(emailTable.mailsLoginCode(steve));
        assertThat(emails.code(steve, Emails.Purpose.LOGIN)).isEmpty();
    }
}
