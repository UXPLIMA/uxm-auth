package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.state.AuthState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChangePasswordTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final String ADDRESS = "203.0.113.7";

    private final Hashing hashing = new Hashing(new BcryptHasher(4), List.of());
    private final HashingLane lane = new HashingLane(1, 1, 8);
    private final Sessions sessions = new Sessions();
    private final MemoryAccounts accounts = new MemoryAccounts();
    private final UUID connection = UUID.randomUUID();

    @AfterEach
    void stopTheLane() {
        lane.close();
    }

    private ChangePassword change() {
        return new ChangePassword(
                accounts, hashing, lane, sessions, () -> PasswordPolicy.ANY, new Heard(), new NoSessions());
    }

    private Account heldToReplace(String weak) {
        Account account = new Account(
                UUID.randomUUID(),
                new PlayerName("Steve"),
                AccountKind.CRACKED,
                null,
                hashing.hash(Password.of(weak)),
                NOW.minus(Duration.ofDays(3)),
                ADDRESS,
                NOW,
                ADDRESS);
        accounts.insert(account);
        sessions.open(connection, new AuthState.Replacing(account, new PasswordVerdict.TooShort(8)));
        return account;
    }

    @Test
    void aWeakPasswordIsReplacedAndThePlayerIsIn() {
        Account steve = heldToReplace("abc");

        ChangeResult result = change().replaceWeak(connection, Password.of("a much better one"))
                .join();

        assertThat(result).isEqualTo(new ChangeResult.Replaced());
        assertThat(sessions.state(connection)).containsInstanceOf(AuthState.Authenticated.class);
        PasswordHash stored = accounts.findById(steve.id()).orElseThrow().password();
        assertThat(stored).isNotEqualTo(steve.password());
    }

    @Test
    void aPasswordStaffSetMeanwhileStandsAndThePlayerLogsInAgain() {
        Account steve = heldToReplace("abc");
        PasswordHash staffs = hashing.hash(Password.of("set by staff"));
        assertThat(accounts.replacePassword(steve.id(), steve.password(), staffs))
                .isTrue();

        ChangeResult result =
                change().replaceWeak(connection, Password.of("mine now")).join();

        assertThat(result).isEqualTo(new ChangeResult.ChangedElsewhere());
        assertThat(accounts.findById(steve.id()).orElseThrow().password()).isEqualTo(staffs);
        assertThat(sessions.state(connection)).containsInstanceOf(AuthState.Replacing.class);
    }

    /** Bulletins that go nowhere but a list. */
    private static final class Heard implements NetworkEvents {
        private final List<Bulletin> told = new ArrayList<>();

        @Override
        public void publish(Bulletin bulletin) {
            told.add(bulletin);
        }

        @Override
        public void listen(Consumer<Bulletin> listener) {}
    }

    /** No remembered sessions at all. */
    private static final class NoSessions implements RememberedSessions {
        @Override
        public void remember(UUID account, String address, Instant until) {}

        @Override
        public boolean holds(UUID account, String address, Instant now) {
            return false;
        }

        @Override
        public void forget(UUID account) {}

        @Override
        public Optional<Boolean> choice(UUID account) {
            return Optional.empty();
        }

        @Override
        public void choose(UUID account, boolean remember) {}
    }
}
