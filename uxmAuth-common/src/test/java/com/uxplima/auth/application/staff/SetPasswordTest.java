package com.uxplima.auth.application.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.auth.application.port.BreachedPasswords;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.testing.AuthFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Staff set an account's password: the rules apply, the hash is replaced, and every session ends. */
final class SetPasswordTest {

    private final AuthFixture fixture = new AuthFixture(1);
    private final List<Bulletin> published = new ArrayList<>();
    private final NetworkEvents events = new NetworkEvents() {
        @Override
        public void publish(Bulletin bulletin) {
            published.add(bulletin);
        }

        @Override
        public void listen(Consumer<Bulletin> listener) {}
    };
    private final SetPassword set = new SetPassword(
            fixture.accounts,
            fixture.hashing,
            () -> fixture.policy,
            BreachedPasswords.NONE,
            fixture.remembered,
            events);

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
    @DisplayName("the new password replaces the old, the remembered sessions go, and every session ends")
    void thePasswordIsSet() {
        UUID steve = steve();
        fixture.remembered.remember(steve, "10.0.0.1", AuthFixture.NOW.plusSeconds(600));

        assertThat(set.set(new PlayerName("steve"), Password.of("fresh7777"))).isEqualTo(new SetPassword.Outcome.Set());

        var stored = fixture.accounts.findById(steve).orElseThrow().password();
        assertThat(fixture.hashing.verify(Password.of("fresh7777"), java.util.Objects.requireNonNull(stored)))
                .isInstanceOf(Verification.Right.class);
        assertThat(fixture.remembered.holds(steve, "10.0.0.1", AuthFixture.NOW)).isFalse();
        assertThat(published).containsExactly(new Bulletin.SessionsEnded(steve, Optional.empty()));
    }

    @Test
    @DisplayName("a password the rules refuse is refused with the reason, and an unknown name says so")
    void refusals() {
        UUID steve = steve();
        var before = fixture.accounts.findById(steve).orElseThrow().password();

        assertThat(set.set(new PlayerName("Steve"), Password.of("abc")))
                .isInstanceOfSatisfying(
                        SetPassword.Outcome.Rejected.class,
                        rejected -> assertThat(rejected.verdict()).isNotInstanceOf(PasswordVerdict.Accepted.class));
        assertThat(fixture.accounts.findById(steve).orElseThrow().password()).isEqualTo(before);
        assertThat(set.set(new PlayerName("Nobody"), Password.of("fresh7777")))
                .isEqualTo(new SetPassword.Outcome.Unknown());
        assertThat(published).isEmpty();
    }
}
