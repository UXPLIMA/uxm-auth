package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.ConflictStrategy;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.velocity.testing.ProxyWorld;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

/** The door's answers become what Velocity does: online or offline mode, the profile, and the refusals. */
final class DoorListenerTest {

    private static final InetSocketAddress FROM = new InetSocketAddress(InetAddress.getLoopbackAddress(), 40001);

    private final ProxyWorld world;
    private final InboundConnection connection = mock(InboundConnection.class);
    private final Logger logger = mock(Logger.class);
    private final ProxySessions sessions = new ProxySessions();

    DoorListenerTest(@TempDir Path folder) {
        this.world = new ProxyWorld(folder);
        when(connection.getRemoteAddress()).thenReturn(FROM);
        when(connection.getVirtualHost())
                .thenReturn(Optional.of(InetSocketAddress.createUnresolved(ProxyWorld.PLAY, 25565)));
    }

    @AfterEach
    void close() {
        world.close();
    }

    private DoorListener listener() {
        return new DoorListener(world.door(), sessions, world.words, logger);
    }

    private PreLoginEvent preLoginOf(String name) {
        // The id a client sends with its hello; an offline client derives it from the name.
        return new PreLoginEvent(
                connection, name, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    @DisplayName("a paid name is put in online mode, and the proxy hands the backend Mojang's profile")
    void aPaidNameGoesOnline() {
        DoorListener listener = listener();
        PreLoginEvent preLogin = preLoginOf("Notch");

        listener.preLogin(preLogin);
        GameProfileRequestEvent profile =
                new GameProfileRequestEvent(connection, new GameProfile(ProxyWorld.MOJANG, "Notch", List.of()), true);
        listener.profile(profile);

        assertThat(preLogin.getResult().isOnlineModeAllowed()).isTrue();
        assertThat(profile.getGameProfile().getId()).isEqualTo(ProxyWorld.MOJANG);
    }

    @Test
    @DisplayName("a cracked name is kept in offline mode, under the mark when the domain rule gives one")
    void aCrackedNameStaysOffline() {
        world.rule = NameRule.DOMAIN;
        DoorListener listener = listener();
        PreLoginEvent preLogin = preLoginOf("Notch");

        listener.preLogin(preLogin);
        GameProfileRequestEvent profile = new GameProfileRequestEvent(
                connection, new GameProfile(UuidScheme.offline(new PlayerName("Notch")), "Notch", List.of()), false);
        listener.profile(profile);

        assertThat(preLogin.getResult().isForceOfflineMode()).isTrue();
        assertThat(profile.getGameProfile().getName()).isEqualTo("Notch+");
    }

    @Test
    @DisplayName("a refused name is turned away at the pre-login with the rule it broke")
    void aBadNameIsTurnedAway() {
        PreLoginEvent preLogin = preLoginOf("no");

        listener().preLogin(preLogin);

        assertThat(preLogin.getResult().isAllowed()).isFalse();
        assertThat(preLogin.getResult().getReasonComponent())
                .hasValueSatisfying(reason -> assertThat(plain(reason)).contains("3"));
    }

    @Test
    @DisplayName("a connection another plugin put in offline mode, as Floodgate does a Bedrock player, is left alone")
    void aBedrockConnectionIsLeftAlone() {
        PreLoginEvent preLogin = preLoginOf("Notch");
        preLogin.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());

        listener().preLogin(preLogin);

        assertThat(preLogin.getResult().isForceOfflineMode()).isTrue();
    }

    @Test
    @DisplayName("a proved owner the conflict rule stops is turned away at the login with the reason")
    void aBlockedOwnerIsTurnedAwayAtTheLogin() {
        world.rule = NameRule.STRICT;
        world.conflict = ConflictStrategy.BLOCK;
        AccountRepository.Insert unused = world.accounts.insert(new Account(
                UUID.randomUUID(),
                new PlayerName("Notch"),
                AccountKind.CRACKED,
                null,
                null,
                world.clock.instant(),
                "10.0.0.1",
                null,
                null));
        DoorListener listener = listener();
        listener.preLogin(preLoginOf("Notch"));
        listener.profile(
                new GameProfileRequestEvent(connection, new GameProfile(ProxyWorld.MOJANG, "Notch", List.of()), true));
        Player player = mock(Player.class);
        when(player.getRemoteAddress()).thenReturn(FROM);
        LoginEvent login = new LoginEvent(player, "");

        listener.login(login);

        assertThat(login.getResult().isAllowed()).isFalse();
    }

    @Test
    @DisplayName("a door that fails turns the connection away with the lookup reason and says why in the console")
    void aFailingDoorTurnsAway() {
        world.close();
        DoorListener listener = listener();
        PreLoginEvent preLogin = preLoginOf("Notch");

        listener.preLogin(preLogin);

        assertThat(preLogin.getResult().isAllowed()).isFalse();
        org.mockito.Mockito.verify(logger)
                .warn(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("Notch"),
                        org.mockito.ArgumentMatchers.any(Throwable.class));
    }

    private LoginEvent loginFrom(DoorListener listener) {
        Player player = mock(Player.class);
        when(player.getRemoteAddress()).thenReturn(FROM);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        LoginEvent login = new LoginEvent(player, "");
        listener.login(login);
        return login;
    }

    @Test
    @DisplayName("a connection the door put in online mode that reaches the login without a proved profile is refused")
    void anUnfinishedConnectionIsRefusedAtTheLogin() {
        DoorListener listener = listener();
        listener.preLogin(preLoginOf("Notch"));

        assertThat(loginFrom(listener).getResult().isAllowed()).isFalse();
    }

    @Test
    @DisplayName("a profile that cannot be made turns the connection away at the login, and says why in the console")
    void aFailingProfileIsRefusedAtTheLogin() {
        DoorListener listener = listener();
        listener.preLogin(preLoginOf("Notch"));
        world.close();

        listener.profile(
                new GameProfileRequestEvent(connection, new GameProfile(ProxyWorld.MOJANG, "Notch", List.of()), true));

        assertThat(loginFrom(listener).getResult().isAllowed()).isFalse();
        org.mockito.Mockito.verify(logger)
                .warn(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("Notch"),
                        org.mockito.ArgumentMatchers.any(Throwable.class));
    }

    @Test
    @DisplayName("an offline profile that cannot be made is turned away at the login too")
    void aFailingOfflineProfileIsRefused() {
        world.web = new com.uxplima.auth.domain.premium.PremiumAnswer.Cracked();
        ProxyDoor door = org.mockito.Mockito.spy(world.door());
        org.mockito.Mockito.doThrow(new IllegalStateException("broken"))
                .when(door)
                .profile(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(false),
                        org.mockito.ArgumentMatchers.any());
        DoorListener listener = new DoorListener(door, sessions, world.words, logger);
        listener.preLogin(preLoginOf("Steve"));

        listener.profile(new GameProfileRequestEvent(
                connection, new GameProfile(UuidScheme.offline(new PlayerName("Steve")), "Steve", List.of()), false));

        assertThat(loginFrom(listener).getResult().isAllowed()).isFalse();
    }

    @Test
    @DisplayName("a connection the door decided and then lost track of is refused at the login rather than let through")
    void aForgottenConnectionIsRefusedAtTheLogin() {
        world.web = new com.uxplima.auth.domain.premium.PremiumAnswer.Cracked();
        DoorListener listener = listener();
        listener.preLogin(preLoginOf("Steve"));
        world.clock.pass(java.time.Duration.ofMinutes(2));
        listener.door()
                .preLogin("Alex", new InetSocketAddress(InetAddress.getLoopbackAddress(), 40009), ProxyWorld.PLAY);

        listener.profile(new GameProfileRequestEvent(
                connection, new GameProfile(UuidScheme.offline(new PlayerName("Steve")), "Steve", List.of()), false));

        assertThat(loginFrom(listener).getResult().isAllowed()).isFalse();
    }

    @Test
    @DisplayName("a cracked connection the door let through, and one another plugin decided, log in")
    void decidedConnectionsLogIn() {
        world.web = new com.uxplima.auth.domain.premium.PremiumAnswer.Cracked();
        DoorListener listener = listener();
        listener.preLogin(preLoginOf("Steve"));
        listener.profile(new GameProfileRequestEvent(
                connection, new GameProfile(UuidScheme.offline(new PlayerName("Steve")), "Steve", List.of()), false));
        assertThat(loginFrom(listener).getResult().isAllowed()).isTrue();

        DoorListener fresh = listener();
        PreLoginEvent bedrock = preLoginOf("Notch");
        bedrock.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
        fresh.preLogin(bedrock);
        assertThat(loginFrom(fresh).getResult().isAllowed()).isTrue();
    }

    @Test
    @DisplayName("a name with a control character reaches the console without it")
    void aNameIsCleanedForTheConsole() {
        assertThat(DoorListener.printable("Not\nch\u001b[31m")).isEqualTo("Not?ch?[31m");
    }

    @Test
    @DisplayName(
            "a proved player is logged in on the proxy at the login, and a cracked one waits with the door's flags")
    void theLoginRecordsWhereThePlayerStands() {
        DoorListener listener = listener();
        listener.preLogin(preLoginOf("Notch"));
        listener.profile(
                new GameProfileRequestEvent(connection, new GameProfile(ProxyWorld.MOJANG, "Notch", List.of()), true));
        Player premium = mock(Player.class);
        when(premium.getRemoteAddress()).thenReturn(FROM);
        when(premium.getUniqueId()).thenReturn(ProxyWorld.MOJANG);
        listener.login(new LoginEvent(premium, ""));
        assertThat(sessions.loggedIn(ProxyWorld.MOJANG)).isTrue();

        world.rule = NameRule.DOMAIN;
        DoorListener marking = listener();
        marking.preLogin(preLoginOf("Steve"));
        UUID marked = UuidScheme.offline(new PlayerName("Steve+"));
        marking.profile(new GameProfileRequestEvent(
                connection, new GameProfile(UuidScheme.offline(new PlayerName("Steve")), "Steve", List.of()), false));
        Player cracked = mock(Player.class);
        when(cracked.getRemoteAddress()).thenReturn(FROM);
        when(cracked.getUniqueId()).thenReturn(marked);
        marking.login(new LoginEvent(cracked, ""));
        assertThat(sessions.loggedIn(marked)).isFalse();
        assertThat(sessions.nextStanding(marked))
                .contains(new com.uxplima.auth.domain.network.Claim.Waiting(false, false, true));
    }

    @Test
    @DisplayName("a refused login records nothing, and a player who leaves the proxy is forgotten there")
    void aRefusedOrGonePlayerHasNoSession() {
        DoorListener listener = listener();
        listener.preLogin(preLoginOf("Notch"));
        Player player = mock(Player.class);
        when(player.getRemoteAddress()).thenReturn(FROM);
        when(player.getUniqueId()).thenReturn(ProxyWorld.MOJANG);
        listener.login(new LoginEvent(player, ""));
        assertThat(sessions.nextStanding(ProxyWorld.MOJANG)).isEmpty();

        sessions.loggedIn(ProxyWorld.MOJANG, com.uxplima.auth.domain.state.AuthMethod.PREMIUM);
        listener.left(new DisconnectEvent(player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN));
        assertThat(sessions.loggedIn(ProxyWorld.MOJANG)).isFalse();
    }

    @Test
    @DisplayName("a player who leaves is forgotten")
    void aPlayerWhoLeavesIsForgotten() {
        DoorListener listener = listener();
        listener.preLogin(preLoginOf("Notch"));
        listener.profile(
                new GameProfileRequestEvent(connection, new GameProfile(ProxyWorld.MOJANG, "Notch", List.of()), true));
        assertThat(listener.door().arrival(FROM)).isPresent();
        Player player = mock(Player.class);
        when(player.getRemoteAddress()).thenReturn(FROM);
        when(player.getUniqueId()).thenReturn(ProxyWorld.MOJANG);

        listener.left(new DisconnectEvent(player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN));

        assertThat(listener.door().arrival(FROM)).isEmpty();
    }
}
