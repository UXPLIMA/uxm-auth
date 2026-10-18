package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.state.AuthMethod;
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
    // A forced login answers on the lane's thread, so both are read across threads.
    private final List<String> told = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
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

        @Override
        public void draw(com.uxplima.auth.domain.protection.CaptchaPicture picture) {
            events.add("drawn");
        }
    };

    private final com.uxplima.auth.adapter.outbound.SqlRememberedSessions remembered;
    private final com.uxplima.auth.application.protection.Remember remember;

    LimboVisitTest(@TempDir Path folder) {
        this.world = new ProxyWorld(folder);
        this.remembered = new com.uxplima.auth.adapter.outbound.SqlRememberedSessions(world.database, world.clock);
        this.remember = new com.uxplima.auth.application.protection.Remember(
                remembered,
                () -> new com.uxplima.auth.application.protection.Remember.Settings(
                        com.uxplima.auth.application.protection.Remember.Mode.EVERYONE,
                        java.time.Duration.ofMinutes(60)),
                com.uxplima.auth.domain.protection.SharedAddresses::none,
                world.clock::instant);
        sessions.waiting(STEVE, new Claim.Waiting(false, false, false));
    }

    @AfterEach
    void close() {
        world.close();
    }

    private LimboVisit visit() {
        return visit(LimboVisit.Reach.NONE);
    }

    private LimboVisit visit(LimboVisit.Reach reach) {
        return new LimboVisit(
                world.limboLogin(true),
                sessions,
                remember,
                STEVE,
                new PlayerName("Steve"),
                "127.0.0.1",
                "play.example.org",
                Audience.empty(),
                exit,
                reach);
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
    @DisplayName("a player the door let past the password by a session types only the code, and leaves logged in by it")
    void aPlayerOwingTheCodeTypesOnlyTheCode() throws Exception {
        LimboVisit first = visit();
        first.arrive();
        settle(first.said("/register hunter22 hunter22"));
        first.left();
        com.uxplima.auth.domain.factor.TotpSecret secret = world.appOn(new PlayerName("Steve"));
        events.clear();
        sessions.waiting(STEVE, new Claim.Waiting(false, false, false));
        sessions.code(STEVE, AuthMethod.SESSION);

        LimboVisit visit = visit();
        visit.arrive();
        settle(visit.said("/2fa " + world.codeNow(secret)));

        assertThat(events).containsExactly("released");
        assertThat(sessions.loggedIn(STEVE)).isTrue();
        assertThat(sessions.nextStanding(STEVE)).contains(new Claim.LoggedIn(AuthMethod.SESSION));
    }

    @Test
    @DisplayName("a captcha that stands is drawn on the map at the arrival, and a wrong code draws a new one")
    void aCaptchaIsDrawnAtTheArrival() throws Exception {
        world.captchaSettings = new com.uxplima.auth.application.protection.Captchas.Settings(
                com.uxplima.auth.application.protection.Captchas.When.REGISTER,
                1,
                5,
                "ABCDEFGHJKMNPQRSTUVWXYZ23456789",
                java.time.Duration.ofMinutes(60),
                com.uxplima.auth.application.protection.Captchas.JavaScreen.MAP);
        LimboVisit visit = visit();

        visit.arrive();
        assertThat(events).containsExactly("drawn");
        assertThat(told).anySatisfy(line -> assertThat(line).contains("/captcha"));

        settle(visit.said("/captcha WRONG"));

        assertThat(events).containsExactly("drawn", "drawn");
        assertThat(sessions.loggedIn(STEVE)).isFalse();
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

    @Test
    @DisplayName("a waiting player is reminded what to type, and the visit knows whether they have an account")
    void aWaitingPlayerIsReminded() throws Exception {
        LimboVisit first = visit();
        first.arrive();
        assertThat(first.registered()).isFalse();
        told.clear();
        first.remind();
        assertThat(told).singleElement().satisfies(line -> assertThat(line).contains("/register"));
        settle(first.said("/register hunter22 hunter22"));
        first.left();

        sessions.waiting(STEVE, new Claim.Waiting(false, false, false));
        LimboVisit again = visit();
        again.arrive();
        told.clear();
        again.remind();
        assertThat(again.registered()).isTrue();
        assertThat(told).singleElement().satisfies(line -> assertThat(line).contains("/login"));
    }

    @Test
    @DisplayName("a player whose handshake failed reads why when they arrive")
    void theDoorsWordReachesTheVisit() {
        sessions.waiting(STEVE, new Claim.Waiting(false, true, false));

        visit().arrive();

        assertThat(told).hasSize(2);
        assertThat(told.getFirst()).contains("paid");
    }

    @Test
    @DisplayName("a player already let out is reminded of nothing")
    void aPlayerLetOutIsNotReminded() throws Exception {
        LimboVisit visit = visit();
        visit.arrive();
        settle(visit.said("/register hunter22 hunter22"));
        told.clear();

        visit.remind();

        assertThat(told).isEmpty();
    }

    @Test
    @DisplayName("a login in the waiting world is remembered from the player's address")
    void aLoginInTheWorldIsRemembered() throws Exception {
        LimboVisit visit = visit();
        visit.arrive();

        settle(visit.said("/register hunter22 hunter22"));

        assertThat(remembered.holds(STEVE, "127.0.0.1", world.clock.instant())).isTrue();
    }

    @Test
    @DisplayName("staff letting the account in release its waiting player as forced; a visit that left hears nothing")
    void staffLetThePlayerOut() throws Exception {
        LimboVisit first = visit();
        first.arrive();
        settle(first.said("/register hunter22 hunter22"));
        first.left();
        events.clear();
        told.clear();
        sessions.waiting(STEVE, new Claim.Waiting(false, false, false));
        com.uxplima.auth.application.staff.ForceLogin force = new com.uxplima.auth.application.staff.ForceLogin(
                world.accounts, world.sessions, new com.uxplima.auth.adapter.outbound.LocalEvents(), world.clock);
        LimboVisits visits = new LimboVisits((id, address) ->
                world.lane.submit(() -> force.admit(id, address).isPresent()));
        LimboVisit visit = visit(visits);
        visit.arrive();

        visits.accept(new com.uxplima.auth.domain.network.Bulletin.LetIn(STEVE));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && !events.contains("released")) {
            Thread.onSpinWait();
        }

        assertThat(events).containsExactly("released");
        assertThat(sessions.nextStanding(STEVE)).contains(new Claim.LoggedIn(AuthMethod.FORCED));
        assertThat(told).anySatisfy(line -> assertThat(line).contains("Staff let you in"));
    }

    @Test
    @DisplayName("a visit that left is forgotten, and another visit's leaving does not forget it")
    void aVisitThatLeftIsForgotten() {
        LimboVisits visits = new LimboVisits((id, address) -> CompletableFuture.completedFuture(true));
        LimboVisit visit = visit(visits);
        visit.arrive();

        visits.left(STEVE, visit());
        visits.accept(new com.uxplima.auth.domain.network.Bulletin.LetIn(STEVE));
        assertThat(events).as("another visit leaving leaves this one known").containsExactly("released");

        events.clear();
        LimboVisit again = visit(visits);
        again.arrive();
        visits.left(STEVE, again);
        visits.accept(new com.uxplima.auth.domain.network.Bulletin.LetIn(STEVE));
        assertThat(events).as("a visit that left is forgotten").isEmpty();
    }
}
