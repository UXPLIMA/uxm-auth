package com.uxplima.auth.paper.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

import io.papermc.paper.connection.PlayerLoginConnection;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.SqlDisputes;
import com.uxplima.auth.application.LetBedrockIn;
import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.premium.AtTheDoor;
import com.uxplima.auth.application.premium.LetPremiumIn;
import com.uxplima.auth.application.premium.Moves;
import com.uxplima.auth.application.premium.NameMemory;
import com.uxplima.auth.application.premium.PremiumLookup;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.ConflictStrategy;
import com.uxplima.auth.domain.premium.Decision;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.premium.NameOwnership;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.premium.Refusal;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.premium.WhenUnknown;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.handshake.HelloOutcome;
import com.uxplima.auth.paper.adapter.outbound.bedrock.BedrockLink;
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
    private static final InetSocketAddress MINE = new InetSocketAddress(InetAddress.getLoopbackAddress(), 50001);

    private ServerMock server;
    private AuthWorld world;
    private boolean sameAddress;
    private BedrockAdmission bedrock = BedrockAdmission.NONE;
    private final FakeHellos hellos = new FakeHellos();
    private @org.jspecify.annotations.Nullable AtTheDoor door;
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
    @DisplayName("a name with an account waits for its password, in the case it was registered in")
    void aKnownNameWaitsForItsPassword() throws Exception {
        // Not yet joined: a pre-login comes before the player exists.
        PlayerMock steve = new PlayerMock(server, "Steve");
        world.registered(steve, "hunter22");
        world.sessions.close(steve.getUniqueId());

        AsyncPlayerPreLoginEvent event = preLogin("Steve", steve.getUniqueId());
        listener(world.accounts).onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        assertThat(world.sessions.state(steve.getUniqueId())).containsInstanceOf(AuthState.Registered.class);
    }

    @Test
    @DisplayName("a registered name in another case is refused at the door, and the reason names the right case")
    void anotherCaseIsRefusedNamingTheRightOne() throws Exception {
        PlayerMock steve = new PlayerMock(server, "Steve");
        world.registered(steve, "hunter22");
        world.sessions.close(steve.getUniqueId());
        UUID other = UUID.randomUUID();

        AsyncPlayerPreLoginEvent event = preLogin("STEVE", other);
        listener(world.accounts).onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        assertThat(plain(event.kickMessage())).contains("Steve").doesNotContain("STEVE");
        assertThat(world.sessions.isOpen(other)).isFalse();
    }

    @Test
    @DisplayName("a name the operator's rules refuse is turned away at the door with the reason, holding no session")
    void aNameTheRulesRefuseIsTurnedAway() throws Exception {
        UUID shortName = UUID.randomUUID();
        UUID oddName = UUID.randomUUID();
        ConnectionListener listener = listener(world.accounts);

        AsyncPlayerPreLoginEvent tooShort = preLogin("ab", shortName);
        listener.onPreLogin(tooShort);
        AsyncPlayerPreLoginEvent odd = preLogin("Ste-ve", oddName);
        listener.onPreLogin(odd);

        assertThat(tooShort.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        assertThat(plain(tooShort.kickMessage())).contains("3");
        assertThat(odd.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        assertThat(plain(odd.kickMessage())).isNotEqualTo(plain(tooShort.kickMessage()));
        assertThat(world.sessions.isOpen(shortName)).isFalse();
        assertThat(world.sessions.isOpen(oddName)).isFalse();
    }

    @Test
    @DisplayName("a Bedrock name passes the pattern through the Bedrock door, even while auto-login is off")
    void aBedrockNamePassesThePatternThroughTheBedrockDoor() throws Exception {
        UUID id = UUID.randomUUID();
        bedrock = new BedrockAdmission(
                floodgate(), new LetBedrockIn(world.accounts, () -> AuthWorld.MAX_ATTEMPTS, world.clock), () -> false);

        AsyncPlayerPreLoginEvent event = preLogin(".Steve", id);
        listener(world.accounts).onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        assertThat(world.sessions.state(id)).contains(new AuthState.Unregistered(new PlayerName(".Steve")));
    }

    @Test
    @DisplayName("a Java connection under a Bedrock-looking name is still held to the pattern")
    void aJavaConnectionUnderABedrockNameIsHeldToThePattern() throws Exception {
        UUID id = UUID.randomUUID();

        AsyncPlayerPreLoginEvent event = preLogin(".Steve", id);
        listener(world.accounts).onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
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
    @DisplayName("a name is held from the door until its connection closes, joined or not")
    void aNameIsHeldUntilItsConnectionCloses() throws Exception {
        UUID id = UUID.randomUUID();
        PlayerLoginConnection first = loginConnection(new InetSocketAddress(ELSEWHERE, 40001), true);
        ConnectionListener listener = listener(world.accounts);
        listener.onPreLogin(preLogin("Newcomer", id, first));

        AsyncPlayerPreLoginEvent second = preLogin(
                "Newcomer", id, loginConnection(new InetSocketAddress(InetAddress.getLoopbackAddress(), 40002), true));
        listener.onPreLogin(second);
        assertThat(second.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);

        when(first.isConnected()).thenReturn(false);
        AsyncPlayerPreLoginEvent third = preLogin(
                "Newcomer", id, loginConnection(new InetSocketAddress(InetAddress.getLoopbackAddress(), 40003), true));
        listener.onPreLogin(third);
        assertThat(third.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
    }

    @Test
    @DisplayName("a connection that closes before it joins leaves no session and no opening behind")
    void aConnectionClosedBeforeJoiningLeavesNothing() throws Exception {
        UUID id = UUID.randomUUID();
        PlayerLoginConnection connection = loginConnection(new InetSocketAddress(ELSEWHERE, 40001), true);
        ConnectionListener listener = listener(world.accounts);
        listener.onPreLogin(preLogin("Newcomer", id, connection));
        assertThat(world.sessions.isOpen(id)).isTrue();

        when(connection.isConnected()).thenReturn(false);
        listener.onClose(new PlayerConnectionCloseEvent(id, "Newcomer", ELSEWHERE, false));

        assertThat(world.sessions.isOpen(id)).isFalse();
        assertThat(listener.openings()).isZero();
    }

    @Test
    @DisplayName("the close of a refused second connection leaves the first one's session and opening alone")
    void aRefusedConnectionsCloseLeavesTheFirst() throws Exception {
        UUID id = UUID.randomUUID();
        ConnectionListener listener = listener(world.accounts);
        listener.onPreLogin(preLogin("Newcomer", id, loginConnection(new InetSocketAddress(ELSEWHERE, 40001), true)));

        listener.onClose(new PlayerConnectionCloseEvent(id, "Newcomer", ELSEWHERE, false));

        assertThat(world.sessions.isOpen(id)).isTrue();
        assertThat(listener.openings()).isOne();
    }

    @Test
    @DisplayName("a player whose opening a later connection replaced is sent away at join, never held as the newcomer")
    void aReplacedOpeningIsSentAwayAtJoin() throws Exception {
        sameAddress = true;
        PlayerMock steve = new PlayerMock(server, "Steve");
        world.registered(steve, "hunter22");
        world.sessions.close(steve.getUniqueId());
        InetSocketAddress mine = MINE;
        ConnectionListener listener = listener(world.accounts);
        listener.onPreLogin(preLogin("Steve", steve.getUniqueId(), loginConnection(mine, true)));
        listener.onPreLogin(preLogin(
                "Steve",
                steve.getUniqueId(),
                loginConnection(new InetSocketAddress(mine.getAddress(), mine.getPort() + 1), true)));
        long newcomer = world.sessions.held(steve.getUniqueId()).orElseThrow().generation();
        server.addPlayer(steve);
        steve.setAddress(MINE);

        listener.onJoin(new PlayerJoinEvent(steve, Component.empty()));
        world.scheduler.drain();

        assertThat(steve.isOnline()).isFalse();
        assertThat(listener.openingOf(steve)).isEmpty();
        assertThat(world.sessions.held(steve.getUniqueId()).orElseThrow().generation())
                .isEqualTo(newcomer);
    }

    @Test
    @DisplayName("a player is bound to the opening their own connection made at the door")
    void aJoinBindsThePlayerToTheirOpening() throws Exception {
        PlayerMock steve = new PlayerMock(server, "Steve");
        world.registered(steve, "hunter22");
        world.sessions.close(steve.getUniqueId());
        ConnectionListener listener = listener(world.accounts);
        listener.onPreLogin(preLogin("Steve", steve.getUniqueId(), loginConnection(MINE, true)));
        long opening = world.sessions.held(steve.getUniqueId()).orElseThrow().generation();
        server.addPlayer(steve);
        steve.setAddress(MINE);

        listener.onJoin(new PlayerJoinEvent(steve, Component.empty()));
        world.scheduler.drain();

        assertThat(listener.openingOf(steve)).hasValue(opening);
    }

    @Test
    @DisplayName("a Bedrock player Floodgate vouches for is let in at the door while auto-login is on")
    void aBedrockPlayerIsLetInAtTheDoor() throws Exception {
        UUID id = UUID.randomUUID();
        bedrock = new BedrockAdmission(
                floodgate(), new LetBedrockIn(world.accounts, () -> AuthWorld.MAX_ATTEMPTS, world.clock), () -> true);

        listener(world.accounts).onPreLogin(preLogin(".Steve", id));

        assertThat(world.sessions.state(id)).containsInstanceOf(AuthState.Authenticated.class);
    }

    @Test
    @DisplayName("a Bedrock player let in at the door is welcomed in once they stand in the world")
    void aBedrockPlayerIsWelcomedAtJoin() {
        PlayerMock steve = server.addPlayer(".Steve");
        world.sessions.open(
                steve.getUniqueId(),
                new LetBedrockIn(world.accounts, () -> AuthWorld.MAX_ATTEMPTS, world.clock)
                        .admit(steve.getUniqueId(), new PlayerName(".Steve"), false, "10.0.0.7"));

        listener(world.accounts).onJoin(new PlayerJoinEvent(steve, Component.empty()));
        world.scheduler.drain();

        assertThat(world.fired).containsExactly(AuthSettings.Moment.LOGGED_IN);
    }

    @Test
    @DisplayName("a connection Mojang proved is let in to its premium account at the door, under Mojang's UUID")
    void aProvedConnectionIsLetIn() throws Exception {
        hellos.installed = true;
        UUID mojang = UUID.randomUUID();
        PlayerLoginConnection connection = loginConnection(MINE, true);
        hellos.outcomes.put(MINE, new HelloOutcome.Proved());

        AsyncPlayerPreLoginEvent event = preLogin(server.createProfile(mojang, "Notch"), connection);
        listener(world.accounts).onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        assertThat(world.sessions.state(mojang))
                .hasValueSatisfying(state -> assertThat(state)
                        .isInstanceOfSatisfying(
                                AuthState.Authenticated.class,
                                in -> assertThat(in.method()).isEqualTo(AuthMethod.PREMIUM)));
        assertThat(event.getPlayerProfile().getId()).isEqualTo(mojang);
    }

    @Test
    @DisplayName("an owner who turned their cracked account premium plays under its own UUID, with Mojang's skin")
    void anOwnerPlaysUnderTheirOwnUuid() throws Exception {
        hellos.installed = true;
        PlayerMock steve = new PlayerMock(server, "Notch");
        world.registered(steve, "hunter22");
        world.sessions.close(steve.getUniqueId());
        ConnectionListener listener = listener(world.accounts);
        atTheDoor(world.accounts, new SqlDisputes(world.database))
                .ownerAsks(MINE.getAddress().getHostAddress(), new PlayerName("Notch"));
        hellos.outcomes.put(MINE, new HelloOutcome.Proved());
        PlayerProfile mojang = server.createProfile(UUID.randomUUID(), "Notch");
        mojang.setProperty(new ProfileProperty("textures", "skin", "signature"));

        AsyncPlayerPreLoginEvent event = preLogin(mojang, loginConnection(MINE, true));
        listener.onPreLogin(event);

        assertThat(event.getPlayerProfile().getId()).isEqualTo(steve.getUniqueId());
        assertThat(event.getPlayerProfile().getProperties())
                .extracting(ProfileProperty::getName)
                .containsExactly("textures");
        assertThat(world.sessions.state(steve.getUniqueId())).containsInstanceOf(AuthState.Authenticated.class);
    }

    @Test
    @DisplayName("a hello the door turned away is refused at the pre-login with the reason")
    void aRefusedHelloIsTurnedAway() throws Exception {
        hellos.installed = true;
        UUID id = UUID.randomUUID();
        hellos.outcomes.put(MINE, new HelloOutcome.Decided(new Decision.Refuse(Refusal.NAME_KEPT)));

        AsyncPlayerPreLoginEvent event = preLogin("Notch", id, loginConnection(MINE, true));
        listener(world.accounts).onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        assertThat(plain(event.kickMessage())).contains("Change your name");
        assertThat(world.sessions.isOpen(id)).isFalse();
    }

    @Test
    @DisplayName("a paid name refused after a failed try is told where the cracked account that held it went")
    void aRefusedPaidNameIsToldWhereItsAccountWent() throws Exception {
        hellos.installed = true;
        PlayerMock old = new PlayerMock(server, "Notch");
        world.registered(old, "hunter22");
        world.sessions.close(old.getUniqueId());
        world.accounts.rename(old.getUniqueId(), new PlayerName("Notch_"), world.clock.instant());
        hellos.outcomes.put(MINE, new HelloOutcome.Decided(new Decision.Refuse(Refusal.PAID_NAME)));

        AsyncPlayerPreLoginEvent event = preLogin("Notch", UUID.randomUUID(), loginConnection(MINE, true));
        listener(world.accounts).onPreLogin(event);

        assertThat(plain(event.kickMessage())).contains("Notch_");
    }

    @Test
    @DisplayName("where the handshake runs, a pre-login with no hello behind it is turned away, never let in unchecked")
    void aPreLoginWithNoHelloIsTurnedAway() throws Exception {
        hellos.installed = true;
        UUID id = UUID.randomUUID();

        AsyncPlayerPreLoginEvent event = preLogin("Steve", id, loginConnection(MINE, true));
        listener(world.accounts).onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        assertThat(world.sessions.isOpen(id)).isFalse();
    }

    @Test
    @DisplayName("a hello let through while no source could answer registers an unverified account")
    void anUnverifiedHelloRegistersUnverified() throws Exception {
        hellos.installed = true;
        UUID id = UUID.randomUUID();
        hellos.outcomes.put(MINE, new HelloOutcome.Decided(new Decision.Cracked(true, false)));

        listener(world.accounts).onPreLogin(preLogin("Steve", id, loginConnection(MINE, true)));

        assertThat(world.sessions.state(id)).contains(new AuthState.Unregistered(new PlayerName("Steve"), true));
    }

    @Test
    @DisplayName(
            "a player who failed the handshake and came in with a password is told why once they stand in the world")
    void aFailedPlayerIsToldWhy() throws Exception {
        hellos.installed = true;
        PlayerMock steve = new PlayerMock(server, "Steve");
        hellos.outcomes.put(MINE, new HelloOutcome.Decided(new Decision.Cracked(false, true)));
        ConnectionListener listener = listener(world.accounts);
        listener.onPreLogin(preLogin("Steve", steve.getUniqueId(), loginConnection(MINE, true)));
        server.addPlayer(steve);
        steve.setAddress(MINE);

        listener.onJoin(new PlayerJoinEvent(steve, Component.empty()));
        world.scheduler.drain();

        assertThat(said(steve)).contains("/claim");
    }

    @Test
    @DisplayName("a premium player let in at the door is welcomed in once they stand in the world")
    void aPremiumPlayerIsWelcomedAtJoin() throws Exception {
        hellos.installed = true;
        PlayerMock notch = new PlayerMock(server, "Notch");
        hellos.outcomes.put(MINE, new HelloOutcome.Proved());
        ConnectionListener listener = listener(world.accounts);
        listener.onPreLogin(preLogin(server.createProfile(notch.getUniqueId(), "Notch"), loginConnection(MINE, true)));
        server.addPlayer(notch);
        notch.setAddress(MINE);

        listener.onJoin(new PlayerJoinEvent(notch, Component.empty()));
        world.scheduler.drain();

        assertThat(world.fired).containsExactly(AuthSettings.Moment.LOGGED_IN);
    }

    @Test
    @DisplayName("a player whose account moved is told at their next login, once, and the move is marked read")
    void aMovedPlayerIsToldAtTheirLogin() throws Exception {
        hellos.installed = true;
        PlayerMock steve = new PlayerMock(server, "Notch_");
        PlayerMock was = new PlayerMock(server, "Notch");
        world.registered(was, "hunter22");
        world.sessions.close(was.getUniqueId());
        world.accounts.rename(was.getUniqueId(), new PlayerName("Notch_"), world.clock.instant());
        hellos.outcomes.put(MINE, new HelloOutcome.Decided(new Decision.Cracked(false, false)));
        ConnectionListener listener = listener(world.accounts);
        listener.onPreLogin(preLogin("Notch_", steve.getUniqueId(), loginConnection(MINE, true)));
        server.addPlayer(steve);

        world.door.open(steve, AuthSettings.Moment.LOGGED_IN);
        world.scheduler.drain();

        assertThat(said(steve)).contains("Notch_");
        assertThat(world.accounts.untoldRename(was.getUniqueId())).isEmpty();
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
                new Arrivals(
                        new ReadTheName(accounts, AuthWorld::shippedNames, () -> AuthWorld.MAX_ATTEMPTS),
                        bedrock,
                        hellos.installed ? helloDoor(accounts) : new HelloAtTheDoor(),
                        world.messages,
                        world.scheduler,
                        server::createProfile),
                world.notices,
                world.door,
                world.keeper,
                new NameHolders(server, id -> Optional.ofNullable(waitingAtTheDialog.get(id))),
                () -> sameAddress,
                world.messages,
                Logger.getLogger("uxmauth-test"));
    }

    /** The hello outcomes a test hands the pre-login, by the connection's address. */
    private static final class FakeHellos implements Hellos {
        boolean installed;
        final Map<InetSocketAddress, HelloOutcome> outcomes = new HashMap<>();

        @Override
        public boolean installed() {
            return installed;
        }

        @Override
        public Optional<HelloOutcome> take(InetSocketAddress address) {
            return Optional.ofNullable(outcomes.remove(address));
        }
    }

    private HelloAtTheDoor helloDoor(AccountRepository accounts) {
        SqlDisputes disputes = new SqlDisputes(world.database);
        return new HelloAtTheDoor(
                hellos,
                atTheDoor(accounts, disputes),
                new LetPremiumIn(
                        accounts,
                        disputes,
                        () -> ConflictStrategy.RENAME,
                        () -> "_",
                        UuidScheme.KEEP_FIRST,
                        world.clock),
                new Moves(accounts));
    }

    private AtTheDoor atTheDoor(AccountRepository accounts, SqlDisputes disputes) {
        if (door == null) {
            door = new AtTheDoor(
                    accounts,
                    AuthWorld::shippedNames,
                    new PremiumLookup(List::of, () -> Duration.ofMinutes(30), world.clock),
                    () -> new NameOwnership(
                            NameRule.AUTOMATIC,
                            WhenUnknown.CRACKED,
                            new HostPatterns(List.of()),
                            new HostPatterns(List.of())),
                    new NameMemory(() -> Duration.ofMinutes(5), world.clock),
                    new NameMemory(() -> Duration.ofMinutes(10), world.clock),
                    new NameMemory(() -> Duration.ofMinutes(10), world.clock),
                    disputes);
        }
        return door;
    }

    private static AsyncPlayerPreLoginEvent preLogin(PlayerProfile profile, PlayerLoginConnection connection) {
        InetSocketAddress from = Objects.requireNonNull(connection.getClientAddress());
        return new AsyncPlayerPreLoginEvent(
                Objects.requireNonNull(profile.getName()),
                from.getAddress(),
                from.getAddress(),
                Objects.requireNonNull(profile.getId()),
                false,
                profile,
                "localhost",
                connection);
    }

    /** Floodgate vouching for every connection, none of them linked. */
    private static BedrockLink floodgate() {
        return new BedrockLink() {
            @Override
            public Optional<BedrockLink.Player> player(UUID connection) {
                return Optional.of(new BedrockLink.Player(false));
            }

            @Override
            public Optional<String> prefix() {
                return Optional.of(".");
            }
        };
    }

    private static String said(PlayerMock player) {
        StringBuilder all = new StringBuilder();
        Component next;
        while ((next = player.nextComponentMessage()) != null) {
            all.append(PlainTextComponentSerializer.plainText().serialize(next)).append('\n');
        }
        return all.toString();
    }

    private static String plain(@org.jspecify.annotations.Nullable Component text) {
        return PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(text));
    }

    /** The event as the server hands it over. The newest constructor needs a live connection, which a test has not. */
    @SuppressWarnings("removal")
    private static AsyncPlayerPreLoginEvent preLogin(String name, UUID id) throws Exception {
        return preLogin(name, id, InetAddress.getLoopbackAddress());
    }

    private static AsyncPlayerPreLoginEvent preLogin(String name, UUID id, PlayerLoginConnection connection) {
        InetSocketAddress from = Objects.requireNonNull(connection.getClientAddress());
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.getId()).thenReturn(id);
        when(profile.getName()).thenReturn(name);
        return new AsyncPlayerPreLoginEvent(
                name, from.getAddress(), from.getAddress(), id, false, profile, "localhost", connection);
    }

    private static PlayerLoginConnection loginConnection(InetSocketAddress from, boolean connected) {
        PlayerLoginConnection connection = mock(PlayerLoginConnection.class);
        when(connection.getClientAddress()).thenReturn(from);
        when(connection.isConnected()).thenReturn(connected);
        return connection;
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
            throw down();
        }

        @Override
        public Optional<Account> findByPremiumId(UUID premiumId) {
            throw down();
        }

        @Override
        public Insert rename(UUID id, PlayerName to, java.time.Instant at) {
            throw down();
        }

        @Override
        public Optional<PlayerName> renamedFrom(PlayerName old) {
            throw down();
        }

        @Override
        public Optional<Renamed> untoldRename(UUID id) {
            throw down();
        }

        @Override
        public void markRenameTold(UUID id) {
            throw down();
        }

        @Override
        public void delete(UUID id) {
            throw down();
        }

        @Override
        public void moveId(UUID from, UUID to) {
            throw down();
        }

        private static StorageException down() {
            return new StorageException("down", new java.sql.SQLException("connection refused"));
        }
    }
}
