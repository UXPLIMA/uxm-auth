package com.uxplima.auth.application.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.InMemoryEmails;
import com.uxplima.auth.testing.InMemoryFactors;
import com.uxplima.auth.testing.InMemoryLoginHistory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What staff read of one account: who it is, how it logs in, and the other accounts on its addresses. */
final class AccountInfoTest {

    private static final Instant NOW = AuthFixture.NOW;

    private final AuthFixture fixture = new AuthFixture(1);
    private final InMemoryLoginHistory history = new InMemoryLoginHistory();
    private final InMemoryFactors factors = new InMemoryFactors();
    private final InMemoryEmails emails = new InMemoryEmails();
    private AccountInfo.Settings settings = new AccountInfo.Settings(10, 20);
    private final AccountInfo info = new AccountInfo(fixture.accounts, history, factors, emails, () -> settings);

    @AfterEach
    void close() {
        fixture.close();
    }

    private UUID account(String name) {
        fixture.registered(name, "hunter22");
        return fixture.accounts.findByName(new PlayerName(name)).orElseThrow().id();
    }

    @Test
    @DisplayName("an account reads with its logins, newest first, its count, its factors and its masked address")
    void theAccountReads() {
        UUID steve = account("Steve");
        history.record(steve, new LoginHistory.Entry(NOW.minusSeconds(60), "203.0.113.7", AuthMethod.PASSWORD));
        history.record(steve, new LoginHistory.Entry(NOW, "198.51.100.2", AuthMethod.SESSION));
        factors.setApp(steve, new TotpSecret(new byte[20]));
        factors.setBackupCodes(steve, List.of(new PasswordHash("$x$a"), new PasswordHash("$x$b")));
        emails.setAddress(steve, EmailAddress.parse("steve@example.net").orElseThrow());

        AccountInfo.View view = info.of(new PlayerName("steve")).orElseThrow();

        assertThat(view.account().id()).isEqualTo(steve);
        assertThat(view.logins()).isEqualTo(2);
        assertThat(view.recent())
                .extracting(LoginHistory.Entry::method)
                .containsExactly(AuthMethod.SESSION, AuthMethod.PASSWORD);
        assertThat(view.app()).isTrue();
        assertThat(view.backupCodes()).isEqualTo(2);
        assertThat(view.mailedCode()).isFalse();
        assertThat(view.address()).contains("s***@e***.net");
    }

    @Test
    @DisplayName("the other accounts on any of its addresses are named, never the account itself, as many as shown")
    void altsAreNamed() {
        UUID steve = account("Steve");
        UUID alex = account("Alex");
        UUID notch = account("Notch");
        account("Herobrine");
        history.record(steve, new LoginHistory.Entry(NOW, "203.0.113.7", AuthMethod.PASSWORD));
        history.record(steve, new LoginHistory.Entry(NOW.plusSeconds(1), "198.51.100.2", AuthMethod.PASSWORD));
        history.record(alex, new LoginHistory.Entry(NOW, "203.0.113.7", AuthMethod.PASSWORD));
        history.record(notch, new LoginHistory.Entry(NOW, "198.51.100.2", AuthMethod.PASSWORD));

        assertThat(info.of(new PlayerName("Steve")).orElseThrow().alts())
                .extracting(PlayerName::value)
                .containsExactlyInAnyOrder("Alex", "Notch");
        settings = new AccountInfo.Settings(10, 1);
        assertThat(info.of(new PlayerName("Steve")).orElseThrow().alts()).hasSize(1);
    }

    @Test
    @DisplayName("as many recent logins as the operator shows; an unknown name is nobody")
    void recentAndUnknown() {
        UUID steve = account("Steve");
        for (int i = 0; i < 5; i++) {
            history.record(steve, new LoginHistory.Entry(NOW.plusSeconds(i), "203.0.113.7", AuthMethod.PASSWORD));
        }
        settings = new AccountInfo.Settings(3, 20);

        assertThat(info.of(new PlayerName("Steve")).orElseThrow().recent()).hasSize(3);
        assertThat(info.of(new PlayerName("Steve")).orElseThrow().logins()).isEqualTo(5);
        assertThat(info.history(new PlayerName("Steve"), 4))
                .hasValueSatisfying(list -> assertThat(list).hasSize(4));
        assertThat(info.of(new PlayerName("Nobody"))).isEmpty();
        assertThat(info.history(new PlayerName("Nobody"), 4)).isEmpty();
    }
}
