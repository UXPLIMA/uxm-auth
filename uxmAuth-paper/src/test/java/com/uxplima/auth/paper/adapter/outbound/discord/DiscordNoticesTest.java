package com.uxplima.auth.paper.adapter.outbound.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.uxplima.auth.adapter.outbound.DiscordSettings;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What reaches the Discord channel, through a stand-in for Discord on this machine. */
final class DiscordNoticesTest {

    private static final String HOME = "203.0.113.7";

    private HttpServer discord;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> console = new CopyOnWriteArrayList<>();
    private final AtomicInteger answer = new AtomicInteger(204);
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile boolean hang;
    private DiscordSettings settings;
    private DiscordNotices notices;

    @BeforeEach
    void setUp() throws IOException {
        discord = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        discord.createContext("/hook", this::answer);
        discord.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        discord.start();
        settings = settings(true);
        notices = new DiscordNotices(() -> settings, DiscordNotices.WEBHOOK, console::add, 16);
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        notices.close();
        discord.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (hang) {
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
        }
        int status = answer.get();
        if (status == 429) {
            exchange.getResponseHeaders().add("Retry-After", "0.2");
        }
        exchange.sendResponseHeaders(status, -1);
        // Discord answers a webhook with no body.
        exchange.getResponseBody().close();
    }

    private DiscordSettings settings(boolean on) {
        Map<DiscordSettings.Event, DiscordSettings.Notice> events = new EnumMap<>(DiscordSettings.Event.class);
        for (DiscordSettings.Event event : DiscordSettings.Event.values()) {
            events.put(
                    event,
                    new DiscordSettings.Notice(
                            on, event.key() + " {player} {address} {method} {accounts} {time} {server}"));
        }
        return new DiscordSettings("http://127.0.0.1:" + discord.getAddress().getPort() + "/hook", "lobby", events);
    }

    private static void until(BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(done.getAsBoolean()).as("within ten seconds").isTrue();
    }

    @Test
    @DisplayName("each event reaches the channel in the operator's words, the address masked and the server named")
    void eachEventArrives() throws InterruptedException {
        PlayerName steve = new PlayerName("Steve");
        notices.registered(steve, HOME);
        notices.loggedIn(steve, HOME, AuthMethod.SESSION);
        notices.knownAddress(steve, HOME, 3);
        notices.banned(HOME, Duration.ofMinutes(90));
        notices.conflictSettled("Steve");
        until(() -> bodies.size() == 5);

        assertThat(bodies.get(0)).contains("register Steve 203.0.x.x");
        assertThat(bodies.get(1)).contains("login Steve 203.0.x.x session");
        assertThat(bodies.get(2)).contains("known-address Steve 203.0.x.x").contains(" 3 ");
        assertThat(bodies.get(3)).contains("ban").contains("203.0.x.x").contains("1h 30m");
        assertThat(bodies.get(4)).contains("conflict Steve");
        assertThat(bodies).allMatch(body -> body.contains("lobby")).noneMatch(body -> body.contains(HOME));
        assertThat(bodies).allMatch(body -> body.contains("\"parse\":[]"));
        assertThat(console).isEmpty();
    }

    @Test
    @DisplayName("an event switched off, or no url at all, sends nothing")
    void offSendsNothing() throws InterruptedException {
        settings = settings(false);
        notices.loggedIn(new PlayerName("Steve"), HOME, AuthMethod.PASSWORD);
        settings = new DiscordSettings("", "lobby", settings(true).events());
        notices.loggedIn(new PlayerName("Steve"), HOME, AuthMethod.PASSWORD);
        settings = settings(true);
        notices.conflictSettled("Marker");
        until(() -> bodies.size() == 1);

        assertThat(bodies).singleElement().asString().contains("conflict Marker");
        assertThat(console).as("nothing was tried, so nothing failed").isEmpty();
    }

    @Test
    @DisplayName("an endpoint that answers 429 is asked once more after the time it names, then the message is"
            + " dropped with one line in the console")
    void aRateLimitIsRetriedOnceThenDropped() throws InterruptedException {
        answer.set(429);
        notices.conflictSettled("Steve");
        until(() -> console.size() == 1);

        assertThat(bodies).hasSize(2);
        assertThat(console.get(0)).contains("Discord").contains("429").doesNotContain(HOME);
    }

    @Test
    @DisplayName("an endpoint that does not answer never delays the caller, and a full queue drops the oldest with"
            + " one line each")
    void aSlowEndpointDelaysNobody() throws InterruptedException {
        notices.close();
        notices = new DiscordNotices(() -> settings, DiscordNotices.WEBHOOK, console::add, 2);
        hang = true;
        long start = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            notices.conflictSettled("Player" + i);
        }
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(500));
        until(() -> bodies.size() == 1);
        until(() -> console.size() == 2);
        assertThat(console).allMatch(line -> line.contains("dropped"));

        hang = false;
        release.countDown();
        until(() -> bodies.size() == 3);
        assertThat(bodies.get(1)).contains("Player3");
        assertThat(bodies.get(2)).contains("Player4");
    }
}
