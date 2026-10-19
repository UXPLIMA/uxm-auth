package com.uxplima.auth.paper.adapter.inbound.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.adapter.outbound.SqlLoginHistory;
import com.uxplima.auth.application.account.BackupPassword;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** After a login the player reads their previous one, and a premium player gets a backup password once. */
final class AfterLoginNoticesTest {

    private static final DateTimeFormatter DATES =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock notch;
    private LoginHistory history;
    private boolean on = true;
    private boolean bedrock;
    private final List<String> formed = new ArrayList<>();
    private LastLoginNotice lastLogin;
    private BackupPasswordNotice backupNotice;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        notch = server.addPlayer("Notch");
        history = new SqlLoginHistory(world.database);
        lastLogin = new LastLoginNotice(
                history,
                () -> on,
                name -> server.getPlayerExact(name.value()),
                world.scheduler,
                world.messages,
                () -> DATES);
        BackupPassword backup =
                new BackupPassword(world.accounts, world.hashing, () -> world.policy, () -> on, new SecureRandom());
        backupNotice = new BackupPasswordNotice(
                backup,
                world.lane,
                name -> server.getPlayerExact(name.value()),
                world.scheduler,
                world.messages,
                (player, password) -> formed.add(password),
                id -> bedrock);
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private UUID account(AccountKind kind) {
        Account account = new Account(
                UUID.randomUUID(),
                new PlayerName("Notch"),
                kind,
                kind == AccountKind.PREMIUM ? UUID.randomUUID() : null,
                kind == AccountKind.PREMIUM ? null : world.hashing.hash(Password.of("hunter22")),
                Instant.parse("2026-01-01T00:00:00Z"),
                "",
                null,
                null);
        world.accounts.insert(account);
        return account.id();
    }

    private void drainMessages() {
        while (notch.nextComponentMessage() != null) {
            // Nothing before the part under test counts.
        }
    }

    @Test
    @DisplayName("the line names the previous login, masked, and not this one")
    void theLastLoginIsThePreviousOne() {
        UUID id = account(AccountKind.CRACKED);
        Instant before = Instant.parse("2026-10-18T09:30:00Z");
        history.record(id, new LoginHistory.Entry(before, "203.0.113.45", AuthMethod.PASSWORD));
        drainMessages();

        lastLogin.landed(id, new PlayerName("Notch"), "198.51.100.7", AuthMethod.PASSWORD);
        world.scheduler.drain();

        assertThat(notch.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        notch,
                        Keys.ACCOUNT_LAST_LOGIN,
                        Text.placeholder("date", "2026-10-18 09:30"),
                        Text.placeholder("address", "203.0.x.x")));
    }

    @Test
    @DisplayName("a first login, a registration, or the notice off, says nothing")
    void nothingToTell() {
        UUID id = account(AccountKind.CRACKED);
        drainMessages();
        lastLogin.landed(id, new PlayerName("Notch"), "198.51.100.7", AuthMethod.PASSWORD);

        history.record(id, new LoginHistory.Entry(Instant.now(), "203.0.113.45", AuthMethod.PASSWORD));
        lastLogin.landed(id, new PlayerName("Notch"), "198.51.100.7", AuthMethod.REGISTERED);
        on = false;
        lastLogin.landed(id, new PlayerName("Notch"), "198.51.100.7", AuthMethod.PASSWORD);
        world.scheduler.drain();

        assertThat(notch.nextComponentMessage()).isNull();
    }

    @Test
    @DisplayName("a premium login with no password gets one, shown once to its player, which then opens the account")
    void theBackupPasswordIsShownOnce() {
        UUID id = account(AccountKind.PREMIUM);
        drainMessages();

        backupNotice.landed(id, new PlayerName("Notch"), "198.51.100.7", AuthMethod.PREMIUM);
        world.scheduler.settle(world.lane.submit(() -> true));
        world.scheduler.drain();

        String told = PLAIN.serialize(Objects.requireNonNull(notch.nextComponentMessage()));
        Account after = world.accounts.findById(id).orElseThrow();
        assertThat(after.password()).isNotNull();
        // The test catalogue has no theme, so the placeholder stands between its tags as it was put in.
        String password = told.replaceAll(".*<value>(\\S+)<body>\\. Click.*", "$1");
        assertThat(world.hashing.verify(Password.of(password), Objects.requireNonNull(after.password())))
                .isNotInstanceOf(Verification.Wrong.class);

        backupNotice.landed(id, new PlayerName("Notch"), "198.51.100.7", AuthMethod.PREMIUM);
        world.scheduler.settle(world.lane.submit(() -> true));
        world.scheduler.drain();
        assertThat(notch.nextComponentMessage()).as("never shown twice").isNull();
    }

    @Test
    @DisplayName("a Bedrock player gets the backup password in a form too; a login by password gets none")
    void bedrockGetsAForm() {
        UUID id = account(AccountKind.PREMIUM);
        backupNotice.landed(id, new PlayerName("Notch"), "198.51.100.7", AuthMethod.PASSWORD);
        world.scheduler.settle(world.lane.submit(() -> true));
        world.scheduler.drain();
        assertThat(world.accounts.findById(id).orElseThrow().password()).isNull();

        bedrock = true;
        backupNotice.landed(id, new PlayerName("Notch"), "198.51.100.7", AuthMethod.PREMIUM);
        world.scheduler.settle(world.lane.submit(() -> true));
        world.scheduler.drain();

        assertThat(formed)
                .singleElement()
                .satisfies(password -> assertThat(password).hasSizeGreaterThanOrEqualTo(12));
    }
}
