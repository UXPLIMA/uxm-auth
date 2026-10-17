package com.uxplima.auth.paper.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import io.papermc.paper.connection.PlayerLoginConnection;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.network.NetworkSecret;
import com.uxplima.auth.domain.premium.DomainMark;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Behind the proxy a server takes the proxy's signed word for who logged in, and nothing unsigned. */
final class NetworkArrivalsTest {

    private static final InetSocketAddress FROM = new InetSocketAddress(InetAddress.getLoopbackAddress(), 50001);

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private final Claims claims = new Claims(NetworkSecret.parse("q".repeat(43)), Duration.ofSeconds(30));
    private final List<String> console = new ArrayList<>();
    private final Logger logger = Logger.getLogger("uxmauth-network-test");

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                console.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private UuidScheme scheme = UuidScheme.KEEP_FIRST;
    private BedrockAdmission bedrock = BedrockAdmission.NONE;

    private Arrivals arrivals() {
        return new Arrivals(
                new ReadTheName(world.accounts, AuthWorld::shippedNames, () -> AuthWorld.MAX_ATTEMPTS),
                bedrock,
                new HelloAtTheDoor(),
                world.messages,
                world.scheduler,
                server::createProfile,
                () -> new DomainMark("+", false),
                scheme,
                new NetworkDoor(() -> Optional.of(claims), world.accounts::findById, world.clock, logger),
                com.uxplima.auth.application.protection.Admission.none(),
                (account, address, host) -> true);
    }

    private AsyncPlayerPreLoginEvent arriving(UUID id, String name, Optional<String> claim) {
        PlayerProfile profile = server.createProfile(id, name);
        profile.setProperty(new ProfileProperty("textures", "skin"));
        claim.ifPresent(token -> profile.setProperty(new ProfileProperty(NetworkDoor.PROPERTY, token)));
        PlayerLoginConnection connection = mock(PlayerLoginConnection.class);
        when(connection.getClientAddress()).thenReturn(FROM);
        when(connection.isConnected()).thenReturn(true);
        return new AsyncPlayerPreLoginEvent(
                name, FROM.getAddress(), FROM.getAddress(), id, false, profile, "localhost", connection);
    }

    private String signed(UUID id, String name, Claim.Standing standing) {
        return claims.sign(new Claim(id, name, standing, world.clock.instant(), "n0nce"));
    }

    private static Arrivals.Arrival.Opens opens(Arrivals.Arrival arrival) {
        assertThat(arrival).isInstanceOf(Arrivals.Arrival.Opens.class);
        return (Arrivals.Arrival.Opens) arrival;
    }

    @Test
    @DisplayName("a claim that the player logged in opens their account logged in, and the claim leaves the profile")
    void aLoginClaimOpensTheAccount() {
        PlayerMock steve = new PlayerMock(server, "Steve");
        world.registered(steve, "hunter22");
        AsyncPlayerPreLoginEvent event = arriving(
                steve.getUniqueId(),
                "Steve",
                Optional.of(signed(steve.getUniqueId(), "Steve", new Claim.LoggedIn(AuthMethod.NETWORK))));

        Arrivals.Arrival.Opens opened = opens(arrivals().arrive(event));

        assertThat(opened.state())
                .isInstanceOfSatisfying(
                        AuthState.Authenticated.class,
                        in -> assertThat(in.method()).isEqualTo(AuthMethod.NETWORK));
        assertThat(opened.visit()).contains("n0nce");
        assertThat(event.getPlayerProfile().getProperties())
                .extracting(ProfileProperty::getName)
                .containsExactly("textures");
    }

    @Test
    @DisplayName("a waiting claim carries the door's flags to the password, and the proxy's id is kept")
    void aWaitingClaimCarriesTheFlags() {
        UUID given = UUID.randomUUID();
        AsyncPlayerPreLoginEvent event =
                arriving(given, "Newbie", Optional.of(signed(given, "Newbie", new Claim.Waiting(true, false, false))));

        Arrivals.Arrival.Opens opened = opens(arrivals().arrive(event));

        assertThat(opened.state()).isEqualTo(new AuthState.Unregistered(new PlayerName("Newbie"), true));
        assertThat(opened.id()).isEqualTo(given);
        assertThat(opened.visit()).contains("n0nce");
    }

    @Test
    @DisplayName(
            "a registered player the proxy sent waiting waits here too: behind a proxy only its door resumes a session")
    void aWaitingClaimIsNotResumedHere() {
        PlayerMock steve = new PlayerMock(server, "Steve");
        world.registered(steve, "hunter22");
        AsyncPlayerPreLoginEvent event = arriving(
                steve.getUniqueId(),
                "Steve",
                Optional.of(signed(steve.getUniqueId(), "Steve", new Claim.Waiting(false, false, false))));

        assertThat(opens(arrivals().arrive(event)).state()).isInstanceOf(AuthState.Registered.class);
    }

    @Test
    @DisplayName("a marked name from the proxy is read under the mark, though the name rules alone would refuse it")
    void aMarkedNameIsReadUnderTheMark() {
        UUID given = UuidScheme.offline(new PlayerName("Notch+"));
        AsyncPlayerPreLoginEvent event =
                arriving(given, "Notch+", Optional.of(signed(given, "Notch+", new Claim.Waiting(false, false, true))));

        Arrivals.Arrival.Opens opened = opens(arrivals().arrive(event));

        assertThat(opened.state()).isEqualTo(new AuthState.Unregistered(new PlayerName("Notch+")));
        assertThat(opened.id()).isEqualTo(given);
    }

    @Test
    @DisplayName("a forged claim turns the player away, and the console says why")
    void aForgedClaimIsRefused() {
        UUID given = UUID.randomUUID();
        String forged = new Claims(NetworkSecret.parse("z".repeat(43)), Duration.ofSeconds(30))
                .sign(new Claim(given, "Steve", AuthMethod.PREMIUM, world.clock.instant(), "n0nce"));

        Arrivals.Arrival arrival = arrivals().arrive(arriving(given, "Steve", Optional.of(forged)));

        assertThat(arrival).isInstanceOf(Arrivals.Arrival.Refused.class);
        assertThat(console)
                .anySatisfy(line -> assertThat(line).contains("Steve").contains("BAD_SIGNATURE"));
    }

    @Test
    @DisplayName("a login claimed for an account this server cannot find turns the player away")
    void aLoginForNoAccountIsRefused() {
        UUID given = UUID.randomUUID();

        Arrivals.Arrival arrival = arrivals()
                .arrive(arriving(
                        given, "Ghost", Optional.of(signed(given, "Ghost", new Claim.LoggedIn(AuthMethod.PREMIUM)))));

        assertThat(arrival).isInstanceOf(Arrivals.Arrival.Refused.class);
        assertThat(console).anySatisfy(line -> assertThat(line).contains("Ghost"));
    }

    @Test
    @DisplayName("under the random scheme a new name keeps the id the proxy gave it, never one of this server's")
    void theProxysIdIsKept() {
        scheme = UuidScheme.RANDOM;
        UUID given = UUID.randomUUID();

        Arrivals.Arrival.Opens opened = opens(arrivals()
                .arrive(arriving(
                        given,
                        "Newbie",
                        Optional.of(signed(given, "Newbie", new Claim.Waiting(false, false, false))))));

        assertThat(opened.id()).isEqualTo(given);
        assertThat(opened.profile()).isNull();
    }

    @Test
    @DisplayName("a Bedrock player's claim comes off the profile too, before the Bedrock door")
    void aBedrockClaimIsTakenOff() {
        UUID given = UUID.randomUUID();
        bedrock = new BedrockAdmission(
                new com.uxplima.auth.paper.adapter.outbound.bedrock.BedrockLink() {
                    @Override
                    public Optional<com.uxplima.auth.paper.adapter.outbound.bedrock.BedrockLink.Player> player(
                            UUID connection) {
                        return Optional.of(
                                new com.uxplima.auth.paper.adapter.outbound.bedrock.BedrockLink.Player(false));
                    }

                    @Override
                    public Optional<String> prefix() {
                        return Optional.of(".");
                    }
                },
                new com.uxplima.auth.application.LetBedrockIn(
                        world.accounts, () -> AuthWorld.MAX_ATTEMPTS, world.clock),
                () -> true);
        AsyncPlayerPreLoginEvent event =
                arriving(given, ".Steve", Optional.of(signed(given, ".Steve", new Claim.Waiting(false, false, false))));

        arrivals().arrive(event);

        assertThat(event.getPlayerProfile().getProperties())
                .extracting(ProfileProperty::getName)
                .containsExactly("textures");
    }

    @Test
    @DisplayName("a player with no claim is asked for the password as usual")
    void noClaimMeansThePassword() {
        UUID given = UuidScheme.offline(new PlayerName("Steve"));

        Arrivals.Arrival.Opens opened = opens(arrivals().arrive(arriving(given, "Steve", Optional.empty())));

        assertThat(opened.state()).isEqualTo(new AuthState.Unregistered(new PlayerName("Steve")));
        assertThat(opened.visit()).isEmpty();
    }
}
