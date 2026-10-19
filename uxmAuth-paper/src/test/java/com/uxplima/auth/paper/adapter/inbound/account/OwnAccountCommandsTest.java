package com.uxplima.auth.paper.adapter.inbound.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.uxplima.auth.application.account.DeleteOwnAccount;
import com.uxplima.auth.application.account.SwitchToCracked;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.staff.MarkKind;
import com.uxplima.auth.application.staff.Unregister;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
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

/** {@code /unregister} and {@code /cracked}, from the typed line or a Bedrock form to what the player reads. */
final class OwnAccountCommandsTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private final List<Bulletin> published = new ArrayList<>();
    private final List<Keys> forms = new ArrayList<>();
    private @Nullable Consumer<String> typedInForm;
    private boolean bedrock;
    private UnregisterCommand unregister;
    private CrackedCommand cracked;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        NetworkEvents events = new NetworkEvents() {
            @Override
            public void publish(Bulletin bulletin) {
                published.add(bulletin);
            }

            @Override
            public void listen(Consumer<Bulletin> listener) {}
        };
        ProofForm form = (player, title, field, answer) -> {
            forms.add(title);
            typedInForm = answer;
        };
        DeleteOwnAccount delete = new DeleteOwnAccount(
                world.sessions,
                world.accounts,
                world.factors,
                world.factorCheck,
                world.hashing,
                world.lane,
                new Unregister(world.accounts, events),
                () -> Duration.ofSeconds(60),
                world.clock);
        unregister = new UnregisterCommand(delete, form, id -> bedrock, world.replies, world.messages);
        SwitchToCracked toCracked = new SwitchToCracked(
                world.sessions,
                world.accounts,
                world.hashing,
                world.lane,
                world.change,
                new MarkKind(world.accounts, name -> new PremiumAnswer.Unknown(), UuidScheme.OFFLINE, events));
        cracked = new CrackedCommand(toCracked, form, id -> bedrock, world.replies, world.messages);
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private void loggedIn(AccountKind kind) {
        world.registered(steve, "hunter22");
        Account account = world.accounts.findByName(new PlayerName("Steve")).orElseThrow();
        if (kind != account.kind()) {
            account = new Account(
                    account.id(),
                    account.name(),
                    kind,
                    steve.getUniqueId(),
                    account.password(),
                    account.registeredAt(),
                    account.registrationAddress(),
                    null,
                    null);
            world.accounts.update(account);
        }
        world.sessions.open(steve.getUniqueId(), new AuthState.Authenticated(account, AuthMethod.PASSWORD));
    }

    private boolean steveExists() {
        return world.accounts.findByName(new PlayerName("Steve")).isPresent();
    }

    @Test
    @DisplayName("/unregister asks with the time limit, and the password then deletes the account and sends it away")
    void unregisterAsksThenDeletes() {
        loggedIn(AccountKind.CRACKED);

        unregister.ask(steve);
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        steve, Keys.ACCOUNT_DELETE_ASK_PASSWORD, Text.placeholder("seconds", "60")));
        world.scheduler.settle(unregister.confirm(steve, "hunter22"));

        assertThat(steveExists()).isFalse();
        assertThat(published).singleElement().isInstanceOf(Bulletin.Unregistered.class);
        assertThat(((Bulletin.Unregistered) published.get(0)).by()).isEqualTo(Bulletin.By.OWNER);
    }

    @Test
    @DisplayName("a wrong password deletes nothing and says so; a confirmation with no ask before it says to ask")
    void unregisterRefusesWrongAndUnasked() {
        loggedIn(AccountKind.CRACKED);

        world.scheduler.settle(unregister.confirm(steve, "hunter22"));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.ACCOUNT_DELETE_NOT_ASKED));

        unregister.ask(steve);
        steve.nextComponentMessage();
        world.scheduler.settle(unregister.confirm(steve, "hunter23"));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.ACCOUNT_DELETE_WRONG));
        assertThat(steveExists()).isTrue();
    }

    @Test
    @DisplayName("a Bedrock player confirms the deletion in a form instead of typing it")
    void unregisterFromABedrockForm() {
        bedrock = true;
        loggedIn(AccountKind.CRACKED);

        unregister.ask(steve);
        assertThat(forms).containsExactly(Keys.ACCOUNT_DELETE_FORM_TITLE);
        java.util.Objects.requireNonNull(typedInForm).accept("hunter22");
        // The lane runs one task at a time, in order: once this one is done, the confirmation is too.
        world.scheduler.settle(world.lane.submit(() -> true));
        world.scheduler.drain();

        assertThat(steveExists()).isFalse();
    }

    @Test
    @DisplayName("/cracked with the password switches a premium account; a cracked one is told it is not premium")
    void crackedSwitchesAPremiumAccount() {
        loggedIn(AccountKind.PREMIUM);

        world.scheduler.settle(cracked.confirm(steve, "hunter22"));

        assertThat(world.accounts
                        .findByName(new PlayerName("Steve"))
                        .orElseThrow()
                        .kind())
                .isEqualTo(AccountKind.CRACKED);
        assertThat(published).singleElement().isEqualTo(new Bulletin.Rejoin(steve.getUniqueId(), Bulletin.By.OWNER));

        world.sessions.open(
                steve.getUniqueId(),
                new AuthState.Authenticated(
                        world.accounts.findByName(new PlayerName("Steve")).orElseThrow(), AuthMethod.PASSWORD));
        world.scheduler.settle(cracked.confirm(steve, "hunter22"));
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(steve, Keys.ACCOUNT_CRACKED_NOT_PREMIUM));
    }

    @Test
    @DisplayName("/cracked alone tells a Java player how to type it, and gives a Bedrock player a form for it")
    void crackedAloneShowsTheWay() {
        loggedIn(AccountKind.PREMIUM);

        cracked.ask(steve);
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.ACCOUNT_CRACKED_USAGE));

        bedrock = true;
        cracked.ask(steve);
        assertThat(forms).containsExactly(Keys.ACCOUNT_CRACKED_FORM_TITLE);
    }
}
