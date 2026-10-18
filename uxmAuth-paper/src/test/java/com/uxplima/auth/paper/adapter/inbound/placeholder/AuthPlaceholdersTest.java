package com.uxplima.auth.paper.adapter.inbound.placeholder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.bukkit.OfflinePlayer;

import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.factor.FactorStates;
import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** What a scoreboard reads of a player: answered from memory, on any thread, for every key the plugin owns. */
final class AuthPlaceholdersTest {

    private static final Instant MADE = Instant.parse("2026-01-02T03:04:00Z");
    private static final Instant IN = Instant.parse("2026-10-18T09:05:00Z");

    @TempDir
    java.nio.file.Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private UUID id;
    private final Sessions sessions = new Sessions();
    private FactorStates factorStates;
    private Factors factors;
    private AuthPlaceholders placeholders;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
        id = world.accounts.findByName(new PlayerName("Steve")).orElseThrow().id();
        factorStates = new FactorStates(world.factors, world.emails);
        factors = factorStates.watching(world.factors);
        placeholders = new AuthPlaceholders(
                sessions::state,
                factorStates,
                () -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private Account account(AccountKind kind) {
        return new Account(
                id, new PlayerName("Steve"), kind, null, new PasswordHash("x"), MADE, "203.0.113.7", IN, "203.0.113.7");
    }

    private @Nullable String ask(OfflinePlayer player, String key) {
        return placeholders.onRequest(player, key);
    }

    @Test
    @DisplayName("a logged-in player reads every key: the way in, the kind, the second factor and both dates")
    void aLoggedInPlayerReadsEveryKey() {
        Account account = account(AccountKind.PREMIUM);
        sessions.open(steve.getUniqueId(), new AuthState.Authenticated(account, AuthMethod.PREMIUM));
        factorStates.refresh(account.id());

        assertThat(ask(steve, "authenticated")).isEqualTo("true");
        assertThat(ask(steve, "method")).isEqualTo("premium");
        assertThat(ask(steve, "premium")).isEqualTo("true");
        assertThat(ask(steve, "kind")).isEqualTo("premium");
        assertThat(ask(steve, "2fa")).isEqualTo("false");
        assertThat(ask(steve, "registered")).isEqualTo("2026-01-02 03:04");
        assertThat(ask(steve, "last_login")).isEqualTo("2026-10-18 09:05");
        assertThat(ask(steve, "AUTHENTICATED")).as("a key reads in any case").isEqualTo("true");
    }

    @Test
    @DisplayName("a cracked or Bedrock account reads its own kind, and premium false")
    void otherKindsRead() {
        sessions.open(
                steve.getUniqueId(), new AuthState.Authenticated(account(AccountKind.BEDROCK), AuthMethod.BEDROCK));
        assertThat(ask(steve, "kind")).isEqualTo("bedrock");
        assertThat(ask(steve, "premium")).isEqualTo("false");
        assertThat(ask(steve, "method")).isEqualTo("bedrock");
    }

    @Test
    @DisplayName("the second factor follows the player turning the app on and off")
    void theFactorFollowsChanges() {
        Account account = account(AccountKind.CRACKED);
        sessions.open(steve.getUniqueId(), new AuthState.Authenticated(account, AuthMethod.PASSWORD));
        factorStates.refresh(account.id());

        factors.setApp(account.id(), new TotpSecret(new byte[20]));
        assertThat(ask(steve, "2fa")).isEqualTo("true");
        factors.removeApp(account.id());
        assertThat(ask(steve, "2fa")).isEqualTo("false");
    }

    @Test
    @DisplayName("a player still waiting to log in reads not authenticated, and nothing of the account")
    void aWaitingPlayerReadsNothing() {
        sessions.open(steve.getUniqueId(), new AuthState.Registered(account(AccountKind.CRACKED), Attempts.fresh(3)));

        assertThat(ask(steve, "authenticated")).isEqualTo("false");
        for (String key : new String[] {"method", "premium", "kind", "2fa", "registered", "last_login"}) {
            assertThat(ask(steve, key)).as(key).isEmpty();
        }
    }

    @Test
    @DisplayName("a player who never joined reads nothing, from memory alone, on whatever thread asks")
    void aStrangerReadsNothingWithoutAQuery() {
        Factors refusing = (Factors) java.lang.reflect.Proxy.newProxyInstance(
                Factors.class.getClassLoader(), new Class<?>[] {Factors.class}, (proxy, method, args) -> {
                    throw new AssertionError("a placeholder read the database");
                });
        Emails refusingMail = (Emails) java.lang.reflect.Proxy.newProxyInstance(
                Emails.class.getClassLoader(), new Class<?>[] {Emails.class}, (proxy, method, args) -> {
                    throw new AssertionError("a placeholder read the database");
                });
        AuthPlaceholders offline = new AuthPlaceholders(
                sessions::state, new FactorStates(refusing, refusingMail), () -> DateTimeFormatter.ISO_INSTANT);
        OfflinePlayer stranger = server.getOfflinePlayer(UUID.randomUUID());

        assertThat(offline.onRequest(stranger, "authenticated")).isEqualTo("false");
        assertThat(offline.onRequest(stranger, "2fa")).isEmpty();
        assertThat(offline.onRequest(stranger, "registered")).isEmpty();
    }

    @Test
    @DisplayName("a key the plugin does not own is left as written, and so is a request with no player")
    void unknownKeysAreLeftAlone() {
        assertThat(ask(steve, "colour")).isNull();
        assertThat(placeholders.onRequest(null, "authenticated")).isNull();
        assertThat(AuthPlaceholders.IDENTIFIER).isEqualTo("uxmauth");
    }
}
