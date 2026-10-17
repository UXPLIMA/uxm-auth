package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.velocity.testing.ProxyWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A visit to the proxy's waiting world: told what to type, answered, and let out once logged in. */
final class LimboVisitTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    private final ProxyWorld world;
    private final ProxySessions sessions = new ProxySessions();
    private final List<String> told = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final LimboVisit.Exit exit = new LimboVisit.Exit() {
        @Override
        public void tell(Component text) {
            told.add(PlainTextComponentSerializer.plainText().serialize(text));
        }

        @Override
        public void release() {
            events.add("released");
        }

        @Override
        public void sendAway(Component reason) {
            events.add("sent away");
        }
    };

    LimboVisitTest(@TempDir Path folder) {
        this.world = new ProxyWorld(folder);
        sessions.waiting(STEVE, new Claim.Waiting(false, false, false));
    }

    @AfterEach
    void close() {
        world.close();
    }

    private LimboVisit visit() {
        return new LimboVisit(
                world.limboLogin(true), sessions, STEVE, new PlayerName("Steve"), "127.0.0.1", Audience.empty(), exit);
    }

    private static void settle(CompletableFuture<Void> said) throws Exception {
        said.get(10, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("a player arriving is told what to type, and a register lets them out logged in on the proxy")
    void aRegisterLetsThePlayerOut() throws Exception {
        LimboVisit visit = visit();

        visit.arrive();
        settle(visit.said("/register hunter22 hunter22"));

        assertThat(told).anySatisfy(line -> assertThat(line).contains("/register"));
        assertThat(events).containsExactly("released");
        assertThat(sessions.loggedIn(STEVE)).isTrue();
    }

    @Test
    @DisplayName("a wrong answer keeps the player in and tells them why")
    void aWrongAnswerKeepsThePlayerIn() throws Exception {
        LimboVisit visit = visit();
        visit.arrive();

        settle(visit.said("/register hunter22 other222"));

        assertThat(events).isEmpty();
        assertThat(sessions.loggedIn(STEVE)).isFalse();
        assertThat(told).hasSize(2);
    }

    @Test
    @DisplayName("a player out of tries is sent away and stays logged out")
    void aPlayerOutOfTriesIsSentAway() throws Exception {
        LimboVisit first = visit();
        first.arrive();
        settle(first.said("/register hunter22 hunter22"));
        first.left();
        sessions.waiting(STEVE, new Claim.Waiting(false, false, false));
        events.clear();
        LimboVisit visit = visit();
        visit.arrive();

        settle(visit.said("/login wrong111"));
        settle(visit.said("/login wrong222"));
        settle(visit.said("/login wrong333"));

        assertThat(events).containsExactly("sent away");
        assertThat(sessions.loggedIn(STEVE)).isFalse();
    }

    @Test
    @DisplayName("a player whose time runs out is sent away, and one who logged in is not")
    void theTimeRunsOut() throws Exception {
        LimboVisit waiting = visit();
        waiting.arrive();
        waiting.timeUp(Component.text("too long"));
        assertThat(events).containsExactly("sent away");

        events.clear();
        LimboVisit done = visit();
        done.arrive();
        settle(done.said("/register hunter22 hunter22"));
        done.timeUp(Component.text("too long"));
        assertThat(events).containsExactly("released");
    }
}
