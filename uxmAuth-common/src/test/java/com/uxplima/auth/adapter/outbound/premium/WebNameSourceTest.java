package com.uxplima.auth.adapter.outbound.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpServer;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Each web source is read the way it answers, against a local server that answers like it. */
final class WebNameSourceTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final PlayerName NAME = new PlayerName("Notch");

    private record Reply(int status, String body) {}

    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final List<String> paths = new ArrayList<>();
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            synchronized (paths) {
                paths.add(path);
            }
            Reply reply = replies.getOrDefault(path, new Reply(500, ""));
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        client.close();
    }

    private WebNameSource source(PremiumSource kind) {
        return new WebNameSource(
                kind, client, () -> base() + "/" + kind.key() + "/{name}", () -> Duration.ofSeconds(2), () -> true);
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void reply(PremiumSource kind, int status, String body) {
        replies.put("/" + kind.key() + "/Notch", new Reply(status, body));
    }

    @Test
    @DisplayName("Mojang: a profile is premium, no content and not found are cracked, a limit is unknown")
    void mojang() {
        reply(PremiumSource.MOJANG, 200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");
        assertThat(source(PremiumSource.MOJANG).ask(NAME)).isEqualTo(new PremiumAnswer.Premium(NOTCH, "Notch"));
        reply(PremiumSource.MOJANG, 204, "");
        assertThat(source(PremiumSource.MOJANG).ask(NAME)).isEqualTo(new PremiumAnswer.Cracked());
        reply(PremiumSource.MOJANG, 404, "{\"errorMessage\":\"Couldn't find any profile with name Notch\"}");
        assertThat(source(PremiumSource.MOJANG).ask(NAME)).isEqualTo(new PremiumAnswer.Cracked());
        reply(PremiumSource.MOJANG, 429, "");
        assertThat(source(PremiumSource.MOJANG).ask(NAME)).isEqualTo(new PremiumAnswer.Unknown());
        reply(PremiumSource.MOJANG, 503, "");
        assertThat(source(PremiumSource.MOJANG).ask(NAME)).isEqualTo(new PremiumAnswer.Unknown());
    }

    @Test
    @DisplayName("a demo account is not premium")
    void aDemoAccountIsNotPremium() {
        reply(
                PremiumSource.MOJANG,
                200,
                "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\",\"demo\":true}");

        assertThat(source(PremiumSource.MOJANG).ask(NAME)).isEqualTo(new PremiumAnswer.Cracked());
    }

    @Test
    @DisplayName("an answer about another name, or one that cannot be read, is unknown")
    void anAnswerAboutAnotherNameIsUnknown() {
        reply(PremiumSource.MOJANG, 200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Someone\"}");
        assertThat(source(PremiumSource.MOJANG).ask(NAME)).isEqualTo(new PremiumAnswer.Unknown());
        reply(PremiumSource.MOJANG, 200, "<html>busy</html>");
        assertThat(source(PremiumSource.MOJANG).ask(NAME)).isEqualTo(new PremiumAnswer.Unknown());
    }

    @Test
    @DisplayName("PlayerDB: a found player is premium, an unknown name is cracked, a failure behind it is unknown")
    void playerDb() {
        reply(
                PremiumSource.PLAYERDB,
                200,
                "{\"code\":\"player.found\",\"success\":true,\"data\":{\"player\":"
                        + "{\"id\":\"069a79f4-44e9-4726-a5be-fca90e38aaf5\",\"username\":\"Notch\"}}}");
        assertThat(source(PremiumSource.PLAYERDB).ask(NAME)).isEqualTo(new PremiumAnswer.Premium(NOTCH, "Notch"));
        reply(PremiumSource.PLAYERDB, 400, "{\"code\":\"minecraft.invalid_username\",\"success\":false}");
        assertThat(source(PremiumSource.PLAYERDB).ask(NAME)).isEqualTo(new PremiumAnswer.Cracked());
        reply(PremiumSource.PLAYERDB, 500, "{\"code\":\"minecraft.api_failure\",\"success\":false}");
        assertThat(source(PremiumSource.PLAYERDB).ask(NAME)).isEqualTo(new PremiumAnswer.Unknown());
    }

    @Test
    @DisplayName("MineTools: a found id is premium, and an error is unknown, since it says the same for both")
    void mineTools() {
        reply(
                PremiumSource.MINETOOLS,
                200,
                "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\",\"status\":\"OK\"}");
        assertThat(source(PremiumSource.MINETOOLS).ask(NAME)).isEqualTo(new PremiumAnswer.Premium(NOTCH, "Notch"));
        reply(PremiumSource.MINETOOLS, 200, "{\"id\":null,\"name\":null,\"status\":\"ERR\"}");
        assertThat(source(PremiumSource.MINETOOLS).ask(NAME)).isEqualTo(new PremiumAnswer.Unknown());
    }

    @Test
    @DisplayName("Ashcon: a user is premium, not found is cracked")
    void ashcon() {
        reply(PremiumSource.ASHCON, 200, "{\"uuid\":\"069a79f4-44e9-4726-a5be-fca90e38aaf5\",\"username\":\"Notch\"}");
        assertThat(source(PremiumSource.ASHCON).ask(NAME)).isEqualTo(new PremiumAnswer.Premium(NOTCH, "Notch"));
        reply(PremiumSource.ASHCON, 404, "{\"code\":404,\"error\":\"Not Found\"}");
        assertThat(source(PremiumSource.ASHCON).ask(NAME)).isEqualTo(new PremiumAnswer.Cracked());
    }

    @Test
    @DisplayName("a source past its cap is not asked at all, and answers unknown")
    void aCappedSourceIsNotAsked() {
        reply(PremiumSource.MOJANG, 200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");
        WebNameSource capped = new WebNameSource(
                PremiumSource.MOJANG,
                client,
                () -> base() + "/mojang/{name}",
                () -> Duration.ofSeconds(2),
                () -> false);

        assertThat(capped.ask(NAME)).isEqualTo(new PremiumAnswer.Unknown());
        assertThat(paths).isEmpty();
    }

    @Test
    @DisplayName("a source nobody answers is unknown, not an error")
    void anUnreachableSourceIsUnknown() {
        WebNameSource nowhere = new WebNameSource(
                PremiumSource.MOJANG,
                client,
                () -> "http://127.0.0.1:1/mojang/{name}",
                () -> Duration.ofSeconds(2),
                () -> true);

        assertThat(nowhere.ask(NAME)).isEqualTo(new PremiumAnswer.Unknown());
    }

    @Test
    @DisplayName("every source is named in config.conf by its key")
    void everySourceHasItsKey() {
        assertThat(List.of(PremiumSource.values()))
                .extracting(PremiumSource::key)
                .containsExactly("mojang", "playerdb", "minetools", "ashcon");
    }
}
