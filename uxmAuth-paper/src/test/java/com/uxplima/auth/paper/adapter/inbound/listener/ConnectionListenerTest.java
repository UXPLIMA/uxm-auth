package com.uxplima.auth.paper.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.storage.StorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** A connection opens a session, a join is held at the door, and a quit closes the session. */
final class ConnectionListenerTest {

    @TempDir
    Path folder;

    private static final InetAddress ELSEWHERE = address(10, 0, 0, 9);

    private ServerMock server;
    private AuthWorld world;
    private boolean sameAddress;
    private final Map<UUID, InetAddress> waitingAtTheDialog = new HashMap<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a name with an account waits for its password, in any case")
    void aKnownNameWaitsForItsPassword() throws Exception {
        // Not yet joined: a pre-login comes before the player exists.
        PlayerMock steve = new PlayerMock(server, "Steve");
        world.registered(steve, "hunter22");
        world.sessions.close(steve.getUniqueId());

        AsyncPlayerPreLoginEvent event = preLogin("STEVE", steve.getUniqueId());
        listener(world.accounts).onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        assertThat(world.sessions.state(steve.getUniqueId())).containsInstanceOf(AuthState.Registered.class);
    }

    @Test
    @DisplayName("a second connection under a name already online is refused, and the first one's session stays")
    void aSecondConnectionUnderANameOnlineIsRefused() throws Exception {
        PlayerMock steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
        long first = world.sessions.held(steve.getUniqueId()).orElseThrow().generation();

        AsyncPlayerPreLoginEvent event = preLogin("Steve", steve.getUniqueId(), ELSEWHERE);
        listener(world.accounts).onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        assertThat(world.sessions.held(steve.getUniqueId()).orElseThrow().generation())
                .isEqualTo(first);
    }

    @Test
    @DisplayName("a second connection from the same address is let through only when config.conf allows it")
    void aSecondConnectionFromTheSameAddressFollowsTheFile() throws Exception {
        PlayerMock steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
        InetAddress same = Objects.requireNonNull(steve.getAddress()).getAddress();

        AsyncPlayerPreLoginEvent refused = preLogin("Steve", steve.getUniqueId(), same);
        listener(world.accounts).onPreLogin(refused);
        assertThat(refused.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);

        sameAddress = true;
        AsyncPlayerPreLoginEvent allowed = preLogin("Steve", steve.getUniqueId(), same);
        listener(world.accounts).onPreLogin(allowed);
        assertThat(allowed.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
    }

    @Test
    @DisplayName("a name whose connection waits at the dialog before the world counts as online")
    void aNameWaitingAtTheDialogIsOnline() throws Exception {
        UUID id = UUID.randomUUID();
        waitingAtTheDialog.put(id, ELSEWHERE);

        AsyncPlayerPreLoginEvent event = preLogin("Newcomer", id, InetAddress.getLoopbackAddress());
        listener(world.accounts).onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        assertThat(world.sessions.state(id)).isEmpty();
    }

    @Test
    @DisplayName("a name with no account is asked to register")
    void anUnknownNameRegisters() throws Exception {
        UUID id = UUID.randomUUID();

        listener(world.accounts).onPreLogin(preLogin("Newcomer", id));

        assertThat(world.sessions.state(id))
                .hasValueSatisfying(
                        state -> assertThat(state).isEqualTo(new AuthState.Unregistered(new PlayerName("Newcomer"))));
    }

    @Test
    @DisplayName(
            "a database that does not answer keeps the player out, with a reason, rather than at a door that cannot open")
    void aDownDatabaseRefusesTheConnection() throws Exception {
        UUID id = UUID.randomUUID();
        AsyncPlayerPreLoginEvent event = preLogin("Steve", id);

        listener(new Unreachable()).onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        assertThat(world.sessions.isOpen(id)).isFalse();
    }

    @Test
    @DisplayName("a joining player who waits is held at the door")
    void aJoinIsHeld() {
        PlayerMock steve = server.addPlayer("Steve");
        world.unregistered(steve);

        listener(world.accounts).onJoin(new PlayerJoinEvent(steve, Component.empty()));
        world.scheduler.drain();

        assertThat(steve.hasPotionEffect(PotionEffectType.BLINDNESS)).isTrue();
    }

    @Test
    @DisplayName("a joining player who waits is offered the screen for their case, a player let in is offered none")
    void aJoinOffersTheScreen() {
        PlayerMock steve = server.addPlayer("Steve");
        world.unregistered(steve);
        PlayerMock alex = server.addPlayer("Alex");
        world.registered(alex, "hunter22");
        world.sessions.update(
                alex.getUniqueId(),
                state -> new AuthState.Authenticated(((AuthState.Registered) state).account(), AuthMethod.PASSWORD));
        ConnectionListener listener = listener(world.accounts);

        listener.onJoin(new PlayerJoinEvent(steve, Component.empty()));
        listener.onJoin(new PlayerJoinEvent(alex, Component.empty()));
        world.scheduler.drain();

        assertThat(world.offered).singleElement().isInstanceOf(AuthState.Unregistered.class);
    }

    @Test
    @DisplayName("a player let in by the dialog before the world is welcomed once they stand in it")
    void aPlayerLetInByTheDialogIsWelcomedAtJoin() {
        PlayerMock steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
        world.sessions.update(
                steve.getUniqueId(),
                state -> new AuthState.Authenticated(((AuthState.Registered) state).account(), AuthMethod.PASSWORD));
        world.welcomes.keep(steve.getUniqueId(), AuthSettings.Moment.LOGGED_IN);

        listener(world.accounts).onJoin(new PlayerJoinEvent(steve, Component.empty()));
        world.scheduler.drain();

        assertThat(world.fired).containsExactly(AuthSettings.Moment.LOGGED_IN);
        assertThat(world.welcomes.take(steve.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("a quit closes the session and gives back what the hold took")
    void aQuitClosesTheSession() {
        PlayerMock steve = server.addPlayer("Steve");
        steve.setOp(true);
        world.unregistered(steve);
        ConnectionListener listener = listener(world.accounts);
        listener.onJoin(new PlayerJoinEvent(steve, Component.empty()));
        world.scheduler.drain();
        assertThat(steve.isOp()).isFalse();

        listener.onQuit(new PlayerQuitEvent(steve, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        world.scheduler.drain();

        assertThat(steve.isOp()).isTrue();
        assertThat(world.sessions.isOpen(steve.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName("a player already online when the plugin starts is read and held like one who just joined")
    void aPlayerOnlineAtEnableIsAdmitted() {
        PlayerMock steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
        world.sessions.close(steve.getUniqueId());

        listener(world.accounts).admitOnline(steve);
        world.scheduler.drain();

        assertThat(world.sessions.state(steve.getUniqueId())).containsInstanceOf(AuthState.Registered.class);
        assertThat(steve.hasPotionEffect(PotionEffectType.BLINDNESS)).isTrue();
    }

    @Test
    @DisplayName("the quit of a first connection does not close the session a second one under the same id opened")
    void aSecondConnectionIsNotLetInByTheFirstOnesQuit() throws Exception {
        // Only a reconnect from the same address, with the operator's leave, can open a second connection.
        sameAddress = true;
        PlayerMock steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
        ConnectionListener listener = listener(world.accounts);
        listener.onJoin(new PlayerJoinEvent(steve, Component.empty()));
        world.scheduler.drain();
        world.sessions.update(
                steve.getUniqueId(),
                state -> new AuthState.Authenticated(
                        ((AuthState.Registered) state).account(), com.uxplima.auth.domain.state.AuthMethod.PASSWORD));

        listener.onPreLogin(preLogin(
                "Steve",
                steve.getUniqueId(),
                Objects.requireNonNull(steve.getAddress()).getAddress()));
        listener.onQuit(new PlayerQuitEvent(steve, Component.empty(), PlayerQuitEvent.QuitReason.KICKED));

        assertThat(world.sessions.state(steve.getUniqueId())).containsInstanceOf(AuthState.Registered.class);
    }

    @Test
    @DisplayName("a player who joins without a session is read again and held, never let in")
    void aJoinWithoutASessionIsReadAndHeld() {
        PlayerMock steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
        world.sessions.close(steve.getUniqueId());

        listener(world.accounts).onJoin(new PlayerJoinEvent(steve, Component.empty()));
        world.scheduler.drain();

        assertThat(world.sessions.state(steve.getUniqueId())).containsInstanceOf(AuthState.Registered.class);
        assertThat(steve.hasPotionEffect(PotionEffectType.BLINDNESS)).isTrue();
    }

    private ConnectionListener listener(AccountRepository accounts) {
        return new ConnectionListener(
                world.scheduler,
                world.sessions,
                accounts,
                world.door,
                world.keeper,
                () -> AuthWorld.MAX_ATTEMPTS,
                new NameHolders(server, id -> Optional.ofNullable(waitingAtTheDialog.get(id))),
                () -> sameAddress,
                world.messages,
                Logger.getLogger("uxmauth-test"));
    }

    /** The event as the server hands it over. The newest constructor needs a live connection, which a test has not. */
    @SuppressWarnings("removal")
    private static AsyncPlayerPreLoginEvent preLogin(String name, UUID id) throws Exception {
        return preLogin(name, id, InetAddress.getLoopbackAddress());
    }

    @SuppressWarnings("removal")
    private static AsyncPlayerPreLoginEvent preLogin(String name, UUID id, InetAddress from) {
        return new AsyncPlayerPreLoginEvent(name, from, id);
    }

    private static InetAddress address(int a, int b, int c, int d) {
        try {
            return InetAddress.getByAddress(new byte[] {(byte) a, (byte) b, (byte) c, (byte) d});
        } catch (java.net.UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** A database that is down. */
    private static final class Unreachable implements AccountRepository {

        @Override
        public Optional<Account> findByName(PlayerName name) {
            throw new StorageException("down", new java.sql.SQLException("connection refused"));
        }

        @Override
        public Optional<Account> findById(UUID id) {
            throw new StorageException("down", new java.sql.SQLException("connection refused"));
        }

        @Override
        public Insert insert(Account account) {
            throw new StorageException("down", new java.sql.SQLException("connection refused"));
        }

        @Override
        public void update(Account account) {
            throw new StorageException("down", new java.sql.SQLException("connection refused"));
        }

        @Override
        public void recordLogin(UUID id, java.time.Instant at, String address) {
            throw new StorageException("down", new java.sql.SQLException("connection refused"));
        }

        @Override
        public boolean replacePassword(
                UUID id,
                com.uxplima.auth.domain.hash.@org.jspecify.annotations.Nullable PasswordHash expected,
                com.uxplima.auth.domain.hash.PasswordHash fresh) {
            throw new StorageException("down", new java.sql.SQLException("connection refused"));
        }

        @Override
        public int countRegisteredFrom(String address) {
            throw new StorageException("down", new java.sql.SQLException("connection refused"));
        }
    }
}
