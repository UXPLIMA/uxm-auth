package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import com.sun.net.httpserver.HttpServer;
import com.uxplima.auth.domain.password.Password;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** haveibeenpwned is asked by the first five characters of a password's SHA-1, with padding, and fails open. */
final class PwnedPasswordsTest {

    private HttpServer server;
    private final List<String> paths = new ArrayList<>();
    private final List<String> paddings = new ArrayList<>();
    private final List<String> said = new ArrayList<>();
    private int status = 200;
    private String body = "";
    private boolean on = true;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/range/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            paddings.add(exchange.getRequestHeaders().getFirst("Add-Padding"));
            byte[] answer = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, answer.length == 0 ? -1 : answer.length);
            if (answer.length > 0) {
                exchange.getResponseBody().write(answer);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private PwnedPasswords pwned() {
        return new PwnedPasswords(
                HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/range/"),
                () -> on,
                said::add);
    }

    private static String sha1(String password) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-1").digest(password.getBytes(StandardCharsets.UTF_8)))
                .toUpperCase(Locale.ROOT);
    }

    @Test
    @DisplayName("only five hex characters leave, padding is asked for, and a counted suffix is a breach")
    void aCountedSuffixIsABreach() throws Exception {
        String hash = sha1("hunter22");
        body = "0018A45C4D1DEF81644B54AB7F969B88D65:0\r\n" + hash.substring(5)
                + ":42\r\nFFFFF0000000000000000000000000000000:3";

        assertThat(pwned().breached(Password.of("hunter22"))).isTrue();

        assertThat(paths).containsExactly("/range/" + hash.substring(0, 5));
        assertThat(paddings).containsExactly("true");
    }

    @Test
    @DisplayName("a suffix the service pads in with no count, or one it does not list, is no breach")
    void paddingIsNoBreach() throws Exception {
        String hash = sha1("hunter22");
        body = hash.substring(5) + ":0\r\nABCDEF0000000000000000000000000000000:7";

        assertThat(pwned().breached(Password.of("hunter22"))).isFalse();
    }

    @Test
    @DisplayName("a service that does not answer lets the password through and says so once")
    void aDownServiceFailsOpen() {
        status = 503;
        PwnedPasswords pwned = pwned();

        assertThat(pwned.breached(Password.of("hunter22"))).isFalse();
        assertThat(pwned.breached(Password.of("hunter33"))).isFalse();
        assertThat(said).singleElement().asString().contains("haveibeenpwned").doesNotContain("hunter");
    }

    @Test
    @DisplayName("with the check off nothing is asked")
    void offAsksNothing() {
        on = false;

        assertThat(pwned().breached(Password.of("hunter22"))).isFalse();
        assertThat(paths).isEmpty();
    }
}
