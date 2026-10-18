package com.uxplima.auth.adapter.outbound.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import com.sun.net.httpserver.HttpServer;
import com.uxplima.auth.testing.CountryDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** With a licence the country file is fetched from MaxMind into the data folder, and again once it is old. */
final class CountryDownloadTest {

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

    @TempDir
    Path folder;

    private HttpServer server;
    private final List<String> authorizations = new ArrayList<>();
    private final List<String> said = new ArrayList<>();
    private int status = 200;
    private byte[] archive = new byte[0];
    private GeoSettings.Licence licence = new GeoSettings.Licence("123456", "s3cret-licence");

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/download", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = status == 200 ? archive : new byte[0];
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private CountryDownload download() {
        URI from = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/download?suffix=tar.gz");
        return new CountryDownload(
                HttpClient.newHttpClient(),
                from,
                folder,
                () -> licence,
                () -> Duration.ofDays(7),
                Clock.fixed(NOW, ZoneOffset.UTC),
                said::add);
    }

    @Test
    @DisplayName("the file is fetched with the account and the licence, unpacked into the folder and read from there")
    void theFileIsFetched() throws Exception {
        archive = tarGz("GeoLite2-Country_20261014/GeoLite2-Country.mmdb", database(Map.of("203.0.113.0/24", "TR")));

        assertThat(download().refresh()).isEqualTo(CountryDownload.Outcome.FETCHED);

        assertThat(authorizations)
                .containsExactly("Basic "
                        + Base64.getEncoder().encodeToString("123456:s3cret-licence".getBytes(StandardCharsets.UTF_8)));
        try (CountryFile file =
                CountryFile.open(folder.resolve(CountryFile.NAME)).orElseThrow()) {
            assertThat(file.of("203.0.113.7")).contains("TR");
        }
    }

    @Test
    @DisplayName("with no licence nothing is asked, and a file younger than the refresh is not fetched again")
    void nothingIsAskedWithoutReason() throws Exception {
        licence = new GeoSettings.Licence("", "");
        assertThat(download().refresh()).isEqualTo(CountryDownload.Outcome.NO_LICENCE);

        licence = new GeoSettings.Licence("123456", "s3cret-licence");
        Path file = Files.write(folder.resolve(CountryFile.NAME), database(Map.of("203.0.113.0/24", "TR")));
        Files.setLastModifiedTime(file, FileTime.from(NOW.minus(Duration.ofDays(6))));
        assertThat(download().refresh()).isEqualTo(CountryDownload.Outcome.FRESH);

        assertThat(authorizations).isEmpty();
    }

    @Test
    @DisplayName("a refused fetch keeps the file there was, and the console never reads the licence")
    void aRefusedFetchKeepsTheOldFile() throws Exception {
        Path file = Files.write(folder.resolve(CountryFile.NAME), database(Map.of("203.0.113.0/24", "TR")));
        Files.setLastModifiedTime(file, FileTime.from(NOW.minus(Duration.ofDays(8))));
        status = 401;

        assertThat(download().refresh()).isEqualTo(CountryDownload.Outcome.FAILED);

        try (CountryFile kept = CountryFile.open(file).orElseThrow()) {
            assertThat(kept.of("203.0.113.7")).contains("TR");
        }
        assertThat(said)
                .singleElement()
                .asString()
                .contains("401")
                .doesNotContain("s3cret")
                .doesNotContain("123456");
    }

    @Test
    @DisplayName("an archive with no country file in it changes nothing")
    void anArchiveWithoutTheFile() throws Exception {
        archive = tarGz("README.txt", "nothing here".getBytes(StandardCharsets.UTF_8));

        assertThat(download().refresh()).isEqualTo(CountryDownload.Outcome.FAILED);
        assertThat(folder.resolve(CountryFile.NAME)).doesNotExist();
    }

    @Test
    @DisplayName("the country file is found among the other files of the archive")
    void theFileAmongOthers() throws Exception {
        archive = tarGz(List.of(
                Map.entry("GeoLite2-Country_20261014/README.txt", "read me".getBytes(StandardCharsets.UTF_8)),
                Map.entry(
                        "GeoLite2-Country_20261014/GeoLite2-Country.mmdb", database(Map.of("203.0.113.0/24", "TR")))));

        assertThat(download().refresh()).isEqualTo(CountryDownload.Outcome.FETCHED);
    }

    @Test
    @DisplayName("an entry under the right name that is no country database changes nothing")
    void aBrokenFileChangesNothing() throws Exception {
        archive = tarGz("GeoLite2-Country.mmdb", "not a database".getBytes(StandardCharsets.UTF_8));

        assertThat(download().refresh()).isEqualTo(CountryDownload.Outcome.FAILED);
        assertThat(folder.resolve(CountryFile.NAME)).doesNotExist();
    }

    @Test
    @DisplayName("the licence never shows in its own text")
    void theLicenceHidesItself() {
        assertThat(licence.toString()).doesNotContain("s3cret").doesNotContain("123456");
    }

    private byte[] database(Map<String, String> ranges) throws IOException {
        Path written = CountryDatabase.write(Files.createTempFile(folder, "countries", ".mmdb"), ranges);
        return Files.readAllBytes(written);
    }

    private static byte[] tarGz(String name, byte[] content) throws IOException {
        return tarGz(List.of(Map.entry(name, content)));
    }

    /** A gzipped tar of these files, as MaxMind ships the database. */
    private static byte[] tarGz(List<Map.Entry<String, byte[]>> files) throws IOException {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        for (Map.Entry<String, byte[]> file : files) {
            entry(tar, file.getKey(), file.getValue());
        }
        tar.write(new byte[1024]);
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream zipped = new GZIPOutputStream(gz)) {
            zipped.write(tar.toByteArray());
        }
        return gz.toByteArray();
    }

    private static void entry(ByteArrayOutputStream tar, String name, byte[] content) throws IOException {
        byte[] header = new byte[512];
        byte[] path = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(path, 0, header, 0, path.length);
        byte[] mode = "0000644\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(mode, 0, header, 100, mode.length);
        byte[] size = String.format("%011o\0", content.length).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(size, 0, header, 124, size.length);
        header[156] = '0';
        byte[] magic = "ustar\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, header, 257, magic.length);
        tar.write(header);
        tar.write(content);
        tar.write(new byte[(512 - content.length % 512) % 512]);
    }
}
