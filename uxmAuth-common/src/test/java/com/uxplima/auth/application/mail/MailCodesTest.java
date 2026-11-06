package com.uxplima.auth.application.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import com.uxplima.auth.application.MemoryAccounts;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.BreachedPasswords;
import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.MailSends;
import com.uxplima.auth.application.port.Mailer;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.mail.SendTally;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.protection.SharedAddresses;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MailCodesTest {

    private static final Instant START = Instant.parse("2030-01-01T12:00:00Z");
    private static final EmailAddress TO =
            EmailAddress.parse("steve@example.com").orElseThrow();
    private static final MailCodes.Settings SETTINGS =
            new MailCodes.Settings(Duration.ofMinutes(15), 1, Duration.ofSeconds(60), 6, 3);

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final HashingLane lane = new HashingLane(1, 1, 8);
    private final Hashing hashing = new Hashing(new BcryptHasher(4), List.of());
    private final MemoryEmails emails = new MemoryEmails();
    private final List<String> mailed = new ArrayList<>();
    private final Mailer mailer = (kind, to, language, values) -> {
        mailed.add(values.get("code"));
        return CompletableFuture.completedFuture(Mailer.Outcome.SENT);
    };
    private final MailCodes codes = new MailCodes(
            emails,
            new MemorySends(),
            mailer,
            hashing,
            lane,
            () -> SETTINGS,
            (address, host) -> Optional.empty(),
            new SplittableRandom(3),
            now::get);
    private final UUID account = UUID.randomUUID();

    @AfterEach
    void stopTheLane() {
        lane.close();
    }

    @Test
    void aCodeThatUsedItsTriesKeepsItsCooldown() {
        assertThat(issue()).isInstanceOf(MailCodes.Issued.Sent.class);
        assertThat(codes.check(account, Emails.Purpose.RECOVERY, "nope", "203.0.113.7", "")
                        .join())
                .isInstanceOf(MailCodes.Checked.TooManyAttempts.class);
        assertThat(emails.code(account, Emails.Purpose.RECOVERY)).isEmpty();

        assertThat(issue()).isInstanceOf(MailCodes.Issued.Cooldown.class);
        assertThat(mailed).hasSize(1);
    }

    @Test
    void anAccountIsSentNoMoreThanTheDaysCap() {
        for (int sent = 0; sent < 3; sent++) {
            assertThat(issue()).isInstanceOf(MailCodes.Issued.Sent.class);
            now.set(START.plus(Duration.ofMinutes(2L * (sent + 1))));
        }

        assertThat(issue())
                .isInstanceOfSatisfying(
                        MailCodes.Issued.Cooldown.class,
                        cooldown -> assertThat(cooldown.left()).isGreaterThan(Duration.ofHours(23)));

        now.set(START.plus(Duration.ofDays(1)));
        assertThat(issue()).isInstanceOf(MailCodes.Issued.Sent.class);
    }

    @Test
    void aRecoveryAskSaysTheSameWhetherOrNotAnAddressIsLinked() {
        Sessions sessions = new Sessions();
        MemoryAccounts accounts = new MemoryAccounts();
        RecoverPassword recover = new RecoverPassword(
                sessions,
                emails,
                codes,
                accounts,
                hashing,
                lane,
                () -> PasswordPolicy.ANY,
                BreachedPasswords.NONE,
                new NoSessions(),
                new Silent(),
                new RecoveryAsks(() -> 2, SharedAddresses::none, now::get));
        UUID linked = waiting(sessions, accounts, account, "Steve");
        UUID unlinked = waiting(sessions, accounts, UUID.randomUUID(), "Alex");
        emails.setAddress(account, TO);

        RecoverPassword.Outcome withAddress =
                recover.request(linked, "203.0.113.7", Locale.ENGLISH).join();
        RecoverPassword.Outcome without =
                recover.request(unlinked, "203.0.113.7", Locale.ENGLISH).join();

        assertThat(withAddress).isEqualTo(new RecoverPassword.Outcome.Sent(Duration.ofMinutes(15)));
        assertThat(without).isEqualTo(withAddress);
        assertThat(recover.request(unlinked, "203.0.113.7", Locale.ENGLISH).join())
                .isInstanceOf(RecoverPassword.Outcome.Cooldown.class);
        assertThat(mailed).hasSize(1);
    }

    private static UUID waiting(Sessions sessions, MemoryAccounts accounts, UUID id, String name) {
        Account owner = new Account(
                id, new PlayerName(name), AccountKind.CRACKED, null, null, START, "203.0.113.7", null, null);
        accounts.insert(owner);
        UUID connection = UUID.randomUUID();
        sessions.open(connection, new AuthState.Registered(owner, Attempts.fresh(3)));
        return connection;
    }

    private MailCodes.Issued issue() {
        return codes.issue(
                        account,
                        Emails.Purpose.RECOVERY,
                        TO,
                        Mailer.Kind.RECOVERY,
                        Locale.ENGLISH,
                        Map.of("player", "Steve"))
                .join();
    }

    /** The mail table in maps. */
    private static final class MemoryEmails implements Emails {
        private final Map<UUID, EmailAddress> addresses = new HashMap<>();
        private final Map<String, Code> codes = new HashMap<>();

        @Override
        public Optional<EmailAddress> address(UUID account) {
            return Optional.ofNullable(addresses.get(account));
        }

        @Override
        public void setAddress(UUID account, EmailAddress address) {
            addresses.put(account, address);
        }

        @Override
        public void removeAddress(UUID account) {
            addresses.remove(account);
        }

        @Override
        public boolean mailsLoginCode(UUID account) {
            return false;
        }

        @Override
        public void setMailsLoginCode(UUID account, boolean on) {}

        @Override
        public synchronized Optional<Code> code(UUID account, Purpose purpose) {
            return Optional.ofNullable(codes.get(account + purpose.name()));
        }

        @Override
        public synchronized Optional<Code> changeCode(
                UUID account, Purpose purpose, UnaryOperator<Optional<Code>> change) {
            Optional<Code> after = change.apply(code(account, purpose));
            after.ifPresentOrElse(
                    code -> codes.put(account + purpose.name(), code), () -> codes.remove(account + purpose.name()));
            return after;
        }
    }

    /** The send tallies in a map. */
    private static final class MemorySends implements MailSends {
        private final Map<String, SendTally> kept = new HashMap<>();

        @Override
        public synchronized SendTally change(UUID account, Emails.Purpose purpose, UnaryOperator<SendTally> change) {
            SendTally after = change.apply(kept.getOrDefault(account + purpose.name(), SendTally.none()));
            kept.put(account + purpose.name(), after);
            return after;
        }
    }

    /** Bulletins that go nowhere. */
    private static final class Silent implements NetworkEvents {
        @Override
        public void publish(Bulletin bulletin) {}

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
