package com.uxplima.auth.paper.adapter.inbound.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.destroystokyo.paper.ClientOption;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.ScreenFile;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.hold.Welcomes;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The dialog before the world: it holds a connecting client until it answers, leaves, or runs out of time. */
final class ConfigurationGateTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private ScreenSettings settings;
    private AuthSettings.Timeouts timeouts =
            new AuthSettings.Timeouts(Duration.ofSeconds(90), Duration.ofSeconds(60), Duration.ofSeconds(5));
    private final Set<UUID> oldClients = new HashSet<>();
    private final Set<UUID> bedrockClients = new HashSet<>();
    private final List<DialogSpec> shown = new ArrayList<>();
    private final Welcomes welcomes = new Welcomes();
    private PlayerMock steve;
    private @Nullable ScreenAnswers answers;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        settings = new ScreenFile(folder.resolve("screens.conf")).current();
        // Steve has not joined: the configuration phase comes before the player exists.
        steve = new PlayerMock(server, "Steve");
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a waiting client is shown the login screen, and a close ends the wait at once")
    void aCloseEndsTheWait() throws Exception {
        world.registered(steve, "hunter22");
        PlayerConfigurationConnection connection = connection(steve.getUniqueId());
        ConfigurationGate gate = gate();

        CompletableFuture<ConfigurationGate.Outcome> wait =
                gate.open(connection).orElseThrow();
        assertThat(shown)
                .singleElement()
                .satisfies(spec -> assertThat(spec.inputs()).hasSize(1));

        gate.onClose(
                new PlayerConnectionCloseEvent(steve.getUniqueId(), "Steve", InetAddress.getLoopbackAddress(), false));

        assertThat(wait.get(1, TimeUnit.SECONDS)).isEqualTo(ConfigurationGate.Outcome.CLOSED);
    }

    @Test
    @DisplayName("the close of another connection under the same id, refused at the door, leaves the wait alone")
    void anotherConnectionsCloseLeavesTheWait() {
        world.registered(steve, "hunter22");
        PlayerConfigurationConnection connection = connection(steve.getUniqueId());
        when(connection.isConnected()).thenReturn(true);
        ConfigurationGate gate = gate();
        CompletableFuture<ConfigurationGate.Outcome> wait =
                gate.open(connection).orElseThrow();

        gate.onClose(
                new PlayerConnectionCloseEvent(steve.getUniqueId(), "Steve", InetAddress.getLoopbackAddress(), false));

        assertThat(wait).isNotDone();
    }

    @Test
    @Timeout(value = 3, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("a client that does not answer in time is sent away with the timeout reason")
    void aTimeoutKicks() {
        world.registered(steve, "hunter22");
        timeouts = new AuthSettings.Timeouts(Duration.ofSeconds(90), Duration.ofMillis(50), Duration.ofSeconds(5));
        PlayerConfigurationConnection connection = connection(steve.getUniqueId());

        gate().onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));

        verify(connection).disconnect(any(Component.class));
    }

    @Test
    @DisplayName("digits pressed on the pad at the dialog are dropped when the wait ends")
    void theEndOfAWaitDropsThePad() throws Exception {
        world.registered(steve, "hunter22");
        PlayerConfigurationConnection connection = connection(steve.getUniqueId());
        ConfigurationGate gate = gate();
        CompletableFuture<ConfigurationGate.Outcome> wait =
                gate.open(connection).orElseThrow();
        gate.answer(connection, PinPad.digit(4), typed("")).get(5, TimeUnit.SECONDS);
        ScreenAnswers pressed = java.util.Objects.requireNonNull(answers);
        assertThat(pressed.pinPad().peek(steve.getUniqueId())).isPresent();

        gate.onClose(
                new PlayerConnectionCloseEvent(steve.getUniqueId(), "Steve", InetAddress.getLoopbackAddress(), false));
        wait.get(1, TimeUnit.SECONDS);

        assertThat(pressed.pinPad().peek(steve.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("the right password releases the wait, and the way in is kept for the welcome at join")
    void anAnswerReleases() throws Exception {
        world.registered(steve, "hunter22");
        PlayerConfigurationConnection connection = connection(steve.getUniqueId());
        ConfigurationGate gate = gate();
        CompletableFuture<ConfigurationGate.Outcome> wait =
                gate.open(connection).orElseThrow();

        gate.answer(connection, AuthScreens.LOGIN, typed("hunter22")).get(5, TimeUnit.SECONDS);

        assertThat(wait.get(1, TimeUnit.SECONDS)).isEqualTo(ConfigurationGate.Outcome.IN);
        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(welcomes.take(steve.getUniqueId())).contains(AuthSettings.Moment.LOGGED_IN);
        verify(connection, never()).disconnect(any(Component.class));
    }

    @Test
    @DisplayName("a wrong password shows the screen again and the client keeps waiting")
    void aWrongPasswordKeepsWaiting() throws Exception {
        world.registered(steve, "hunter22");
        PlayerConfigurationConnection connection = connection(steve.getUniqueId());
        ConfigurationGate gate = gate();
        CompletableFuture<ConfigurationGate.Outcome> wait =
                gate.open(connection).orElseThrow();

        gate.answer(connection, AuthScreens.LOGIN, typed("nope1234")).get(5, TimeUnit.SECONDS);

        assertThat(shown).hasSize(2);
        assertThat(wait).isNotDone();
    }

    @Test
    @DisplayName("an answer from a connection that is not the one waiting is thrown away")
    void anAnswerFromAnotherConnectionIsIgnored() throws Exception {
        world.registered(steve, "hunter22");
        ConfigurationGate gate = gate();
        CompletableFuture<ConfigurationGate.Outcome> wait =
                gate.open(connection(steve.getUniqueId())).orElseThrow();

        gate.answer(connection(steve.getUniqueId()), AuthScreens.LOGIN, typed("hunter22"))
                .get(5, TimeUnit.SECONDS);

        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(wait).isNotDone();
    }

    @Test
    @DisplayName("a right password typed after another connection opened the session again opens nothing")
    void anAnswerForAReplacedOpeningIsThrownAway() throws Exception {
        world.registered(steve, "hunter22");
        PlayerConfigurationConnection connection = connection(steve.getUniqueId());
        ConfigurationGate gate = gate();
        assertThat(gate.open(connection)).isPresent();
        world.sessions.open(
                steve.getUniqueId(), world.sessions.state(steve.getUniqueId()).orElseThrow());

        gate.answer(connection, AuthScreens.LOGIN, typed("hunter22")).get(5, TimeUnit.SECONDS);

        assertThat(world.isAuthenticated(steve)).isFalse();
    }

    @Test
    @DisplayName("a second connection under an id that already waits here is sent away")
    void aSecondWaitingConnectionIsSentAway() throws Exception {
        world.registered(steve, "hunter22");
        ConfigurationGate gate = gate();
        PlayerConfigurationConnection first = connection(steve.getUniqueId());
        PlayerConfigurationConnection second = connection(steve.getUniqueId());
        CompletableFuture<ConfigurationGate.Outcome> wait = gate.open(first).orElseThrow();

        assertThat(gate.open(second)).isEmpty();

        verify(second).disconnect(any(Component.class));
        assertThat(wait).isNotDone();
        assertThat(gate.waitingFrom(steve.getUniqueId())).isPresent();
        gate.answer(first, AuthScreens.LOGIN, typed("hunter22")).get(5, TimeUnit.SECONDS);
        assertThat(wait.get(1, TimeUnit.SECONDS)).isEqualTo(ConfigurationGate.Outcome.IN);
    }

    @Test
    @DisplayName("a client too old for dialogs is not held here, and the world holds it with the commands")
    void anOldClientPassesThrough() {
        world.registered(steve, "hunter22");
        oldClients.add(steve.getUniqueId());
        ConfigurationGate gate = gate();

        assertThat(gate.open(connection(steve.getUniqueId()))).isEmpty();
        assertThat(shown).isEmpty();
        assertThat(gate.sentToTheWorld(steve.getUniqueId())).isTrue();
    }

    @Test
    @DisplayName("a Bedrock client is not held here, and is still shown its screens in the world as forms")
    void aBedrockClientWaitsInTheWorldWithForms() {
        world.registered(steve, "hunter22");
        bedrockClients.add(steve.getUniqueId());
        ConfigurationGate gate = gate();

        assertThat(gate.open(connection(steve.getUniqueId()))).isEmpty();
        assertThat(shown).isEmpty();
        assertThat(gate.sentToTheWorld(steve.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName("outside dialog mode, and for a client already in, nothing waits here")
    void nothingWaitsOutsideDialogMode() {
        world.registered(steve, "hunter22");
        settings = withRoom(ScreenSettings.WaitingRoom.FROZEN);
        assertThat(gate().open(connection(steve.getUniqueId()))).isEmpty();

        settings = withRoom(ScreenSettings.WaitingRoom.DIALOG);
        world.sessions.update(
                steve.getUniqueId(),
                state -> new AuthState.Authenticated(
                        ((AuthState.Registered) state).account(), com.uxplima.auth.domain.state.AuthMethod.PASSWORD));
        assertThat(gate().open(connection(steve.getUniqueId()))).isEmpty();
    }

    @Test
    @DisplayName("leaving the dialog sends the client into the frozen world when screens.conf says world")
    void cancelToTheWorld() throws Exception {
        world.registered(steve, "hunter22");
        settings = withCancel(ScreenSettings.Cancel.WORLD);
        PlayerConfigurationConnection connection = connection(steve.getUniqueId());
        ConfigurationGate gate = gate();
        CompletableFuture<ConfigurationGate.Outcome> wait =
                gate.open(connection).orElseThrow();

        gate.answer(connection, AuthScreens.CANCEL, typed("")).get(5, TimeUnit.SECONDS);

        assertThat(wait.get(1, TimeUnit.SECONDS)).isEqualTo(ConfigurationGate.Outcome.WORLD);
        assertThat(gate.sentToTheWorld(steve.getUniqueId())).isTrue();
    }

    @Test
    @DisplayName("leaving the dialog sends the client away when screens.conf says kick")
    void cancelKicks() throws Exception {
        world.registered(steve, "hunter22");
        PlayerConfigurationConnection connection = connection(steve.getUniqueId());
        ConfigurationGate gate = gate();
        CompletableFuture<ConfigurationGate.Outcome> wait =
                gate.open(connection).orElseThrow();

        gate.answer(connection, AuthScreens.CANCEL, typed("")).get(5, TimeUnit.SECONDS);

        assertThat(wait.get(1, TimeUnit.SECONDS)).isEqualTo(ConfigurationGate.Outcome.KICKED);
        verify(connection).disconnect(any(Component.class));
    }

    private ConfigurationGate gate() {
        AuthScreens screens =
                new AuthScreens(() -> settings, () -> world.passwords, world.messages, new Styler(Theme.defaults()));
        ScreenShower shower = new ScreenShower() {
            @Override
            public void show(Audience who, DialogSpec spec) {
                shown.add(spec);
            }

            @Override
            public void close(Audience who) {}
        };
        answers = new ScreenAnswers(
                screens,
                world.register,
                world.logIn,
                () -> settings,
                () -> world.passwords,
                world.messages,
                shower,
                id -> world.sessions.state(id).orElse(null) instanceof AuthState.Unregistered);
        return new ConfigurationGate(
                world.sessions,
                () -> settings,
                () -> timeouts,
                screens,
                answers,
                shower,
                world.messages,
                id -> !oldClients.contains(id),
                bedrockClients::contains,
                permission -> true,
                welcomes,
                java.util.logging.Logger.getLogger("uxmauth-test"));
    }

    private static PlayerConfigurationConnection connection(UUID id) {
        PlayerConfigurationConnection connection = mock(PlayerConfigurationConnection.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.getId()).thenReturn(id);
        when(profile.getName()).thenReturn("Steve");
        when(connection.getProfile()).thenReturn(profile);
        when(connection.getAudience()).thenReturn(Audience.empty());
        when(connection.getClientOption(ClientOption.LOCALE)).thenReturn("en_us");
        when(connection.getClientAddress()).thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), 50000));
        return connection;
    }

    private static ScreenAnswers.Answer typed(String password) {
        Map<String, String> texts = Map.of(AuthScreens.PASSWORD, password);
        return new ScreenAnswers.Answer() {
            @Override
            public @Nullable String text(String key) {
                return texts.get(key);
            }

            @Override
            public boolean ticked(String key) {
                return false;
            }
        };
    }

    private ScreenSettings withRoom(ScreenSettings.WaitingRoom room) {
        return new ScreenSettings(
                room,
                settings.cancel(),
                settings.canClose(),
                settings.pinPad(),
                settings.rules(),
                settings.register(),
                settings.login(),
                settings.pin());
    }

    private ScreenSettings withCancel(ScreenSettings.Cancel cancel) {
        return new ScreenSettings(
                settings.waitingRoom(),
                cancel,
                settings.canClose(),
                settings.pinPad(),
                settings.rules(),
                settings.register(),
                settings.login(),
                settings.pin());
    }
}
