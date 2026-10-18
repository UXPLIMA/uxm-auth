package com.uxplima.auth.paper.adapter.inbound.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.Mailer;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.name.PlayerName;
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

/** {@code /email add|confirm|remove}: a code to the address, the address saved by its code, always shown masked. */
final class EmailLinkTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private EmailLink email;
    private final Set<UUID> bedrock = new HashSet<>();
    private @Nullable Consumer<String> addressForm;
    private @Nullable Consumer<String> codeForm;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        email = world.emailLink(
                new EmailLink.Forms() {
                    @Override
                    public void address(Player player, Consumer<String> answer) {
                        addressForm = answer;
                    }

                    @Override
                    public void code(Player player, Consumer<String> answer) {
                        codeForm = answer;
                    }
                },
                bedrock::contains);
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private void loggedIn() {
        world.registered(steve, "hunter22");
        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("hunter22"), "127.0.0.1")
                        .join())
                .isInstanceOf(LoginResult.LoggedIn.class);
    }

    /** Runs the player's thread until the condition holds; at most ten seconds. */
    private void await(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("within ten seconds").isLessThan(deadline);
            world.scheduler.drain();
            Thread.onSpinWait();
        }
    }

    private UUID account() {
        return world.accounts.findByName(new PlayerName("Steve")).orElseThrow().id();
    }

    private String mailedCode() {
        return Objects.requireNonNull(world.mailed.getLast().get("code"), "code");
    }

    @Test
    @DisplayName("add mails a code and says where, masked; confirm with the code links the address")
    void addThenConfirm() {
        loggedIn();

        world.scheduler.settle(email.add(steve, "berke@example.com"));
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        steve,
                        Keys.EMAIL_SENT,
                        Text.placeholder("address", "b***@e***.com"),
                        Text.placeholder("minutes", "15")));
        assertThat(world.emails.address(account())).isEmpty();

        world.scheduler.settle(email.confirm(steve, mailedCode()));
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        steve, Keys.EMAIL_CONFIRMED, Text.placeholder("address", "b***@e***.com")));
        assertThat(world.emails.address(account()))
                .contains(EmailAddress.parse("berke@example.com").orElseThrow());
    }

    @Test
    @DisplayName("a wrong code says the tries left; a second address inside the cooldown says the seconds to wait")
    void wrongCodeAndCooldown() {
        loggedIn();
        world.scheduler.settle(email.add(steve, "berke@example.com"));
        steve.nextComponentMessage();

        world.scheduler.settle(email.confirm(steve, "000000"));
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(steve, Keys.EMAIL_WRONG, Text.placeholder("left", "4")));
        world.scheduler.settle(email.add(steve, "other@example.com"));
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(steve, Keys.EMAIL_COOLDOWN, Text.placeholder("seconds", "60")));
    }

    @Test
    @DisplayName("not an address, a refused domain, mail off and a failed mail are each said, and nothing is linked")
    void refusalsAreSaid() {
        loggedIn();

        world.scheduler.settle(email.add(steve, "not an address"));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.EMAIL_NOT_AN_ADDRESS));
        world.deniedDomains = List.of("example.com");
        world.scheduler.settle(email.add(steve, "berke@example.com"));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.EMAIL_DOMAIN_REFUSED));
        world.deniedDomains = List.of();
        world.mailOutcome = Mailer.Outcome.OFF;
        world.scheduler.settle(email.add(steve, "berke@example.com"));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.EMAIL_MAIL_OFF));
        world.mailOutcome = Mailer.Outcome.FAILED;
        world.scheduler.settle(email.add(steve, "berke@example.com"));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.EMAIL_MAIL_FAILED));
        assertThat(world.emails.address(account())).isEmpty();
    }

    @Test
    @DisplayName("remove takes the address away, said masked; the command alone says what it does and what is linked")
    void removeAndUsage() {
        loggedIn();
        world.emails.setAddress(
                account(), EmailAddress.parse("berke@example.com").orElseThrow());

        world.scheduler.settle(email.usage(steve));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.EMAIL_USAGE));
        assertThat(steve.nextComponentMessage())
                .isEqualTo(
                        world.messages.render(steve, Keys.EMAIL_LINKED, Text.placeholder("address", "b***@e***.com")));
        world.scheduler.settle(email.remove(steve));
        assertThat(steve.nextComponentMessage())
                .isEqualTo(
                        world.messages.render(steve, Keys.EMAIL_REMOVED, Text.placeholder("address", "b***@e***.com")));
        world.scheduler.settle(email.remove(steve));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.EMAIL_NONE_LINKED));
    }

    @Test
    @DisplayName("while the login code goes by mail, add and remove say to turn that off first")
    void theFactorAddressIsKept() {
        loggedIn();
        world.emails.setAddress(
                account(), EmailAddress.parse("berke@example.com").orElseThrow());
        world.emails.setMailsLoginCode(account(), true);

        world.scheduler.settle(email.add(steve, "new@example.com"));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.EMAIL_FACTOR_ON));
        world.scheduler.settle(email.remove(steve));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.EMAIL_FACTOR_ON));
        assertThat(world.mailed).isEmpty();
    }

    @Test
    @DisplayName("a player not logged in is told to log in first, and nothing is mailed")
    void notLoggedIn() {
        world.registered(steve, "hunter22");

        world.scheduler.settle(email.add(steve, "berke@example.com"));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.EMAIL_LOG_IN_FIRST));
        assertThat(world.mailed).isEmpty();
    }

    @Test
    @DisplayName("a Bedrock player types the address in a form, then the code in another")
    void aBedrockPlayerUsesForms() {
        loggedIn();
        bedrock.add(steve.getUniqueId());

        world.scheduler.settle(email.usage(steve));
        Objects.requireNonNull(addressForm).accept("berke@example.com");
        await(() -> codeForm != null);
        Objects.requireNonNull(codeForm).accept(mailedCode());

        await(() -> world.emails.address(account()).isPresent());
        assertThat(world.emails.address(account()))
                .contains(EmailAddress.parse("berke@example.com").orElseThrow());
    }

    @Test
    @DisplayName("enough wrong codes ban the address and send the player away")
    void wrongCodesBan() {
        loggedIn();
        for (int round = 0; round < 4 && steve.isOnline(); round++) {
            world.mailNow = world.mailNow.plusSeconds(120);
            world.scheduler.settle(email.add(steve, "berke@example.com"));
            for (int i = 0; i < 5 && steve.isOnline(); i++) {
                world.scheduler.settle(email.confirm(steve, "000000"));
            }
        }

        assertThat(steve.isOnline()).isFalse();
        assertThat(world.fired).contains(AuthSettings.Moment.BANNED);
        assertThat(world.mailed).allSatisfy(values -> assertThat(values).containsKey("code"));
    }
}
