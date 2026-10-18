package com.uxplima.auth.paper.adapter.outbound.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.auth.api.AccountType;
import com.uxplima.auth.api.LoginWay;
import com.uxplima.auth.api.paper.event.BanEvent;
import com.uxplima.auth.api.paper.event.FailedLoginEvent;
import com.uxplima.auth.api.paper.event.KindSwitchEvent;
import com.uxplima.auth.api.paper.event.LoginEvent;
import com.uxplima.auth.api.paper.event.PasswordChangeEvent;
import com.uxplima.auth.api.paper.event.PreLoginEvent;
import com.uxplima.auth.api.paper.event.PreRegisterEvent;
import com.uxplima.auth.api.paper.event.RegisterEvent;
import com.uxplima.auth.api.paper.event.UnregisterEvent;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Each event fires once, with its facts, on the thread Bukkit expects for it. */
final class PaperEventsTest {

    private ServerMock server;
    private PlayerMock steve;
    private PaperEvents events;
    private final List<String> heard = new CopyOnWriteArrayList<>();
    private volatile String refusal = "";

    /** A plugin of somebody else's, listening. */
    public final class Listening implements Listener {
        @EventHandler
        public void onPreLogin(PreLoginEvent event) {
            heard.add("pre-login " + event.getPlayer().getName() + " async=" + event.isAsynchronous());
            if (!refusal.isEmpty()) {
                event.refuse(refusal);
            }
        }

        @EventHandler
        public void onPreRegister(PreRegisterEvent event) {
            heard.add("pre-register " + event.getPlayer().getName());
            event.setCancelled(true);
        }

        @EventHandler
        public void onLogin(LoginEvent event) {
            heard.add("login " + event.getPlayer().getName() + " " + event.way() + " async=" + event.isAsynchronous());
        }

        @EventHandler
        public void onRegister(RegisterEvent event) {
            heard.add("register " + event.getPlayer().getName());
        }

        @EventHandler
        public void onFailed(FailedLoginEvent event) {
            heard.add("failed " + event.getPlayer().getName() + " " + event.attemptsLeft());
        }

        @EventHandler
        public void onBan(BanEvent event) {
            heard.add("ban " + event.address() + " " + event.length());
        }

        @EventHandler
        public void onChange(PasswordChangeEvent event) {
            heard.add("changed " + event.name());
        }

        @EventHandler
        public void onUnregister(UnregisterEvent event) {
            heard.add("unregistered " + event.name());
        }

        @EventHandler
        public void onSwitch(KindSwitchEvent event) {
            heard.add("switched " + event.name() + " " + event.to());
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin other = MockBukkit.createMockPlugin();
        server.getPluginManager().registerEvents(new Listening(), other);
        steve = server.addPlayer("Steve");
        events = new PaperEvents(server::getPlayer, server.getPluginManager()::callEvent);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Runs on a thread of its own, as the lane does. */
    private static <T> T off(java.util.function.Supplier<T> work) {
        return CompletableFuture.supplyAsync(work).join();
    }

    @Test
    @DisplayName("before a login the listeners are asked off the server's threads, and a refusal comes back")
    void aLoginIsAskedFirst() {
        assertThat(off(() -> events.beforeLogin(steve.getUniqueId()))).isEmpty();
        refusal = "Maintenance.";
        assertThat(off(() -> events.beforeLogin(steve.getUniqueId()))).contains("Maintenance.");
        assertThat(off(() -> events.beforeRegister(steve.getUniqueId()))).contains("");

        assertThat(heard)
                .containsExactly("pre-login Steve async=true", "pre-login Steve async=true", "pre-register Steve");
    }

    @Test
    @DisplayName("a player who is not here is asked about by nobody and refused by nobody")
    void nobodyIsAskedAboutAStranger() {
        UUID stranger = UUID.randomUUID();
        assertThat(off(() -> events.beforeLogin(stranger))).isEmpty();
        off(() -> {
            events.wrongPassword(stranger, 2);
            return null;
        });
        assertThat(heard).isEmpty();
    }

    @Test
    @DisplayName("a wrong password, a ban and every change to an account fire once, with their facts")
    void accountEventsFire() {
        off(() -> {
            events.wrongPassword(steve.getUniqueId(), 2);
            events.banned("203.0.113.7", Duration.ofMinutes(5));
            events.passwordChanged(UUID.randomUUID(), new PlayerName("Steve"));
            events.unregistered(UUID.randomUUID(), new PlayerName("Alex"));
            events.kindSwitched(UUID.randomUUID(), new PlayerName("Notch"), AccountKind.PREMIUM);
            return null;
        });

        assertThat(heard)
                .containsExactly(
                        "failed Steve 2",
                        "ban 203.0.113.7 PT5M",
                        "changed Steve",
                        "unregistered Alex",
                        "switched Notch " + AccountType.PREMIUM);
    }

    @Test
    @DisplayName("a login fires on the player's own thread with its way in, and a registration fires both")
    void loginsFireOnThePlayersThread() {
        events.loggedIn(steve, AuthMethod.SESSION);
        events.loggedIn(steve, AuthMethod.REGISTERED);

        assertThat(heard)
                .containsExactly(
                        "login Steve " + LoginWay.SESSION + " async=false",
                        "register Steve",
                        "login Steve " + LoginWay.REGISTERED + " async=false");
    }
}
