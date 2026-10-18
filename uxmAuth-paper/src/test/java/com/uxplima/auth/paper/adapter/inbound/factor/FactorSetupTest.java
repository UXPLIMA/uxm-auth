package com.uxplima.auth.paper.adapter.inbound.factor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.SecretSeal;
import com.uxplima.auth.adapter.outbound.SqlFactors;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.factor.FactorCheck;
import com.uxplima.auth.application.factor.SetUpTotp;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.factor.BackupCodes;
import com.uxplima.auth.domain.factor.Totp;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** {@code /2fa setup|confirm|off}: the key, the link and the QR map, and nothing kept until the app's code. */
final class FactorSetupTest {

    private static final Totp TOTP = new Totp(6, Duration.ofSeconds(30), 1);

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private SqlFactors factors;
    private FactorSetup setup;
    private final List<String> handed = new ArrayList<>();
    private final List<String> taken = new ArrayList<>();
    private boolean room = true;
    private final Set<UUID> bedrock = new HashSet<>();
    private @Nullable String formKey;
    private @Nullable Consumer<String> formAnswer;
    private List<String> formCodes = List.of();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        factors = new SqlFactors(world.database, SecretSeal.fromPassphrase("lab"), world.clock);
        SetUpTotp.Settings settings =
                new SetUpTotp.Settings("Uxplima", "{player}", TOTP, Duration.ofMinutes(10), new BackupCodes(8, 10));
        FactorCheck check =
                new FactorCheck(factors, world.hashing, settings::totp, settings::backup, FactorCheck.MailedCode.NONE);
        SetUpTotp setUp = new SetUpTotp(
                factors, check, () -> settings, world.bans::failed, new SplittableRandom(5), world.clock::instant);
        FactorSetup.Hand hand = new FactorSetup.Hand() {
            @Override
            public boolean give(Player player, String link) {
                handed.add(link);
                return room;
            }

            @Override
            public void take(Player player) {
                taken.add(player.getName());
            }
        };
        FactorSetup.Form form = new FactorSetup.Form() {
            @Override
            public void show(Player player, String key, int digits, Consumer<String> answer) {
                formKey = key;
                formAnswer = answer;
            }

            @Override
            public void codes(Player player, List<String> codes) {
                formCodes = codes;
            }
        };
        setup = new FactorSetup(
                setUp,
                world.sessions,
                world.owedSetups,
                () -> settings,
                hand,
                form,
                bedrock::contains,
                world.lane,
                world.replies,
                world.moments,
                world.messages);
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private void loggedIn() {
        world.registered(steve, "hunter22");
        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("hunter22"), address())
                        .join())
                .isInstanceOf(LoginResult.LoggedIn.class);
    }

    private String address() {
        return steve.getAddress().getAddress().getHostAddress();
    }

    private Instant now() {
        return world.clock.instant();
    }

    private String code(String base32) {
        return TOTP.code(TotpSecret.fromBase32(base32).bytes(), TOTP.step(now()));
    }

    private static String keyOf(Component message) {
        List<String> copied = new ArrayList<>();
        collect(message, copied);
        return copied.getFirst();
    }

    /** The backup codes of a message, as the click copies them. */
    private static List<String> codesOf(Component message) {
        return List.of(keyOf(message).split(" "));
    }

    private static void collect(Component component, List<String> copied) {
        ClickEvent<?> click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.COPY_TO_CLIPBOARD) {
            copied.add(((ClickEvent.Payload.Text) click.payload()).value());
        }
        component.children().forEach(child -> collect(child, copied));
    }

    @Test
    @DisplayName("setup tells the key and the link, each to click and copy, hands the QR map, and keeps nothing yet")
    void setupTellsTheKeyAndTheLink() {
        loggedIn();

        world.scheduler.settle(setup.setup(steve));

        String key = keyOf(steve.nextComponentMessage());
        assertThat(TotpSecret.fromBase32(key).bytes()).hasSize(Totp.SECRET_BYTES);
        String link = keyOf(steve.nextComponentMessage());
        assertThat(link).startsWith("otpauth://totp/Uxplima:Steve?secret=" + key);
        assertThat(handed).containsExactly(link);
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_MAP));
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(steve, Keys.FACTOR_CONFIRM, Text.placeholder("minutes", "10")));
        assertThat(factors.app(steve.getUniqueId())).isEmpty();
        assertThat(formKey).isNull();
    }

    @Test
    @DisplayName("with no room for the map, the player is told how to make some")
    void noRoomForTheMap() {
        loggedIn();
        room = false;

        world.scheduler.settle(setup.setup(steve));
        steve.nextComponentMessage();
        steve.nextComponentMessage();

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_NO_ROOM));
    }

    @Test
    @DisplayName("the app's code turns it on and the map is taken back; a wrong one is said and changes nothing")
    void theAppsCodeTurnsItOn() {
        loggedIn();
        world.scheduler.settle(setup.setup(steve));
        String key = keyOf(steve.nextComponentMessage());
        drain();

        world.scheduler.settle(setup.confirm(steve, "000000"));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_WRONG));
        assertThat(factors.app(steve.getUniqueId())).isEmpty();

        world.scheduler.settle(setup.confirm(steve, code(key)));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_ON));
        assertThat(factors.app(steve.getUniqueId())).isPresent();
        assertThat(taken).containsExactly("Steve");
        List<String> codes = codesOf(steve.nextComponentMessage());
        assertThat(codes).hasSize(8).allMatch(shown -> shown.matches("[A-HJKMNP-Z2-9]{5}-[A-HJKMNP-Z2-9]{5}"));
        assertThat(factors.backupCodes(steve.getUniqueId())).hasSize(8);
        assertThat(formCodes).as("a Java player gets no form").isEmpty();
    }

    @Test
    @DisplayName("backup hands over a new set for a code and voids the old one; a wrong code is said")
    void backupHandsOverANewSet() {
        loggedIn();
        TotpSecret secret = new TotpSecret(new byte[20]);
        factors.setApp(steve.getUniqueId(), secret);

        world.scheduler.settle(setup.backup(steve, "000000"));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_WRONG));
        world.scheduler.settle(setup.backup(steve, code(secret.base32())));
        List<String> first = codesOf(steve.nextComponentMessage());
        world.scheduler.settle(setup.backup(steve, first.get(0)));
        List<String> second = codesOf(steve.nextComponentMessage());

        assertThat(second).hasSize(8).doesNotContainAnyElementsOf(first);
        world.scheduler.settle(setup.off(steve, first.get(1)));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_WRONG));
        world.scheduler.settle(setup.off(steve, second.get(1)));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_OFF));
    }

    @Test
    @DisplayName("a confirm with no setup waiting says so and takes back any map left behind")
    void aConfirmWithNoSetup() {
        loggedIn();

        world.scheduler.settle(setup.confirm(steve, "123456"));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_NOTHING_STARTED));
        assertThat(taken).containsExactly("Steve");
    }

    @Test
    @DisplayName("a Bedrock player gets the form with the key to copy, and its code turns it on")
    void aBedrockPlayerGetsTheForm() {
        loggedIn();
        bedrock.add(steve.getUniqueId());

        world.scheduler.settle(setup.setup(steve));
        String key = keyOf(steve.nextComponentMessage());
        drain();

        assertThat(formKey).isEqualTo(key);
        java.util.Objects.requireNonNull(formAnswer).accept(code(key));
        assertThat(awaitedMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_ON));
        assertThat(factors.app(steve.getUniqueId())).isPresent();
        assertThat(formCodes)
                .as("the form shows the codes chat shows")
                .isEqualTo(codesOf(steve.nextComponentMessage()));
    }

    @Test
    @DisplayName("an account with the app on is told to turn it off first")
    void anAppAlreadyOn() {
        loggedIn();
        factors.setApp(steve.getUniqueId(), new TotpSecret(new byte[20]));

        world.scheduler.settle(setup.setup(steve));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_ALREADY_ON));
        assertThat(handed).isEmpty();
    }

    @Test
    @DisplayName("off takes the app's code; a wrong one counts against the address and a ban sends the player away")
    void offTakesTheAppsCode() {
        loggedIn();
        TotpSecret secret = new TotpSecret(new byte[20]);
        factors.setApp(steve.getUniqueId(), secret);

        world.scheduler.settle(setup.off(steve, "000000"));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_WRONG));

        world.scheduler.settle(setup.off(steve, code(secret.base32())));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_OFF));
        assertThat(factors.app(steve.getUniqueId())).isEmpty();

        world.scheduler.settle(setup.off(steve, "000000"));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_NOT_ON));
    }

    @Test
    @DisplayName("enough wrong codes to turning it off ban the address, and the player is sent away")
    void wrongCodesBan() {
        loggedIn();
        factors.setApp(steve.getUniqueId(), new TotpSecret(new byte[20]));

        for (int i = 0; i < 12 && steve.isOnline(); i++) {
            world.scheduler.settle(setup.off(steve, "000000"));
        }

        assertThat(steve.isOnline()).isFalse();
        assertThat(world.fired).contains(AuthSettings.Moment.BANNED);
    }

    @Test
    @DisplayName("through a shared-address filter, read from the host the player typed, wrong codes ban nobody")
    void throughAFilterNobodyIsBanned() {
        loggedIn();
        factors.setApp(steve.getUniqueId(), new TotpSecret(new byte[20]));
        com.uxplima.auth.domain.state.AuthState in =
                world.sessions.state(steve.getUniqueId()).orElseThrow();
        world.sessions.open(steve.getUniqueId(), in, "visit", "play.tcpshield.com");
        world.sharedHosts = List.of("*.tcpshield.com");
        world.sharedSources = List.of(address());

        for (int i = 0; i < 12; i++) {
            world.scheduler.settle(setup.off(steve, "000000"));
        }

        assertThat(steve.isOnline()).isTrue();
    }

    @Test
    @DisplayName("a player not logged in is told to log in first, and nothing starts")
    void notLoggedIn() {
        world.registered(steve, "hunter22");

        world.scheduler.settle(setup.setup(steve));
        world.scheduler.settle(setup.off(steve, "123456"));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_LOG_IN_FIRST));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_LOG_IN_FIRST));
        assertThat(handed).isEmpty();
    }

    @Test
    @DisplayName("a player who leaves mid-setup leaves nothing: the setup goes and the map is taken back")
    void leavingEndsTheSetup() {
        loggedIn();
        world.scheduler.settle(setup.setup(steve));
        String key = keyOf(steve.nextComponentMessage());
        drain();

        setup.left(steve);
        world.scheduler.settle(setup.confirm(steve, code(key)));

        assertThat(taken).contains("Steve");
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_NOTHING_STARTED));
    }

    /** The next message, once the lane's answer has reached the player's thread; at most ten seconds. */
    private Component awaitedMessage() {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        Component message;
        while ((message = steve.nextComponentMessage()) == null) {
            assertThat(System.nanoTime()).as("an answer within ten seconds").isLessThan(deadline);
            world.scheduler.drain();
            Thread.onSpinWait();
        }
        return message;
    }

    private void drain() {
        while (steve.nextComponentMessage() != null) {
            // The rest of the setup's lines.
        }
    }
}
