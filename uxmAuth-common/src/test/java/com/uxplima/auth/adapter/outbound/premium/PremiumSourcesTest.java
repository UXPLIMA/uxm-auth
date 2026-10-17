package com.uxplima.auth.adapter.outbound.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.port.NameSource;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.TurnedClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The sources config.conf lists, in its order, with Mojang's cap on Mojang alone. */
final class PremiumSourcesTest {

    private final AtomicInteger requests = new AtomicInteger();
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicReference<AuthSettings.Premium> settings = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        client.close();
    }

    private AuthSettings.Premium premium(List<PremiumSource> order, int cap) {
        String here = "http://127.0.0.1:" + server.getAddress().getPort() + "/{name}";
        return new AuthSettings.Premium(
                order,
                Duration.ofMinutes(30),
                cap,
                Duration.ofSeconds(2),
                Map.of(
                        PremiumSource.MOJANG, here,
                        PremiumSource.PLAYERDB, here,
                        PremiumSource.MINETOOLS, here,
                        PremiumSource.ASHCON, here));
    }

    @Test
    @DisplayName("the sources come in the order config.conf lists them, and a source it leaves out is not there")
    void theOrderIsTheFiles() {
        settings.set(premium(List.of(PremiumSource.ASHCON, PremiumSource.MOJANG), 5));
        PremiumSources sources = new PremiumSources(client, settings::get, new TurnedClock(AuthFixture.NOW));

        assertThat(sources.ordered())
                .map(source -> ((WebNameSource) source).kind())
                .containsExactly(PremiumSource.ASHCON, PremiumSource.MOJANG);
    }

    @Test
    @DisplayName("Mojang stops being asked at its cap, and the other sources are not held to it")
    void onlyMojangIsCapped() {
        settings.set(premium(List.of(PremiumSource.MOJANG), 1));
        PremiumSources sources = new PremiumSources(client, settings::get, new TurnedClock(AuthFixture.NOW));
        NameSource mojang = sources.ordered().getFirst();

        mojang.ask(new PlayerName("Steve"));
        mojang.ask(new PlayerName("Alex"));
        assertThat(requests).hasValue(1);

        settings.set(premium(List.of(PremiumSource.PLAYERDB), 1));
        NameSource playerDb = sources.ordered().getFirst();
        playerDb.ask(new PlayerName("Steve"));
        playerDb.ask(new PlayerName("Alex"));
        assertThat(requests).hasValue(3);
    }
}
