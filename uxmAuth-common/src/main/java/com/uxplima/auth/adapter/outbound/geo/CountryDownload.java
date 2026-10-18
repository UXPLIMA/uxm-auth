package com.uxplima.auth.adapter.outbound.geo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

/**
 * Fetches the GeoLite2 country file from MaxMind into the data folder, with the operator's account and licence
 * key, when the file there is missing or older than the refresh. It blocks while it works, so the platform runs
 * it off the server thread. A fetch that fails keeps the file there was, and says why without the licence.
 */
public final class CountryDownload {

    /** Where MaxMind serves the country file to an account. */
    public static final URI MAXMIND =
            URI.create("https://download.maxmind.com/geoip/databases/GeoLite2-Country/download?suffix=tar.gz");

    /** What a refresh came to. */
    public enum Outcome {
        /** No account or licence key is written, so nothing was asked. */
        NO_LICENCE,
        /** The file there is younger than the refresh. */
        FRESH,
        /** A new file is in the folder. */
        FETCHED,
        /** MaxMind could not be asked, or did not answer with a country file. */
        FAILED
    }

    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private final HttpClient web;
    private final URI from;
    private final Path folder;
    private final Supplier<GeoSettings.Licence> licence;
    private final Supplier<Duration> refresh;
    private final Clock clock;
    private final Consumer<String> console;

    public CountryDownload(
            HttpClient web,
            URI from,
            Path folder,
            Supplier<GeoSettings.Licence> licence,
            Supplier<Duration> refresh,
            Clock clock,
            Consumer<String> console) {
        this.web = Objects.requireNonNull(web, "web");
        this.from = Objects.requireNonNull(from, "from");
        this.folder = Objects.requireNonNull(folder, "folder");
        this.licence = Objects.requireNonNull(licence, "licence");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.console = Objects.requireNonNull(console, "console");
    }

    /** Fetches the file when it is missing or old, and answers what came of it. */
    public Outcome refresh() {
        GeoSettings.Licence account = licence.get();
        if (!account.present()) {
            return Outcome.NO_LICENCE;
        }
        Path target = folder.resolve(CountryFile.NAME);
        if (fresh(target)) {
            return Outcome.FRESH;
        }
        try {
            return fetch(account, target);
        } catch (IOException failed) {
            console.accept("Could not fetch the country file from MaxMind: " + failed.getMessage());
            return Outcome.FAILED;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        }
    }

    private boolean fresh(Path target) {
        try {
            Instant changed = Files.getLastModifiedTime(target).toInstant();
            return Files.isRegularFile(target) && changed.plus(refresh.get()).isAfter(clock.instant());
        } catch (IOException absent) {
            return false;
        }
    }

    private Outcome fetch(GeoSettings.Licence account, Path target) throws IOException, InterruptedException {
        String basic = Base64.getEncoder()
                .encodeToString((account.accountId() + ":" + account.key()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(from)
                .timeout(TIMEOUT)
                .header("Authorization", "Basic " + basic)
                .GET()
                .build();
        HttpResponse<InputStream> response = web.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                console.accept("Could not fetch the country file from MaxMind: it answered " + response.statusCode()
                        + ". Check protection.countries.account-id and licence-key.");
                return Outcome.FAILED;
            }
            Path unpacked = Files.createTempFile(folder, "countries", ".part");
            try {
                if (!TarGz.extract(new GZIPInputStream(body), CountryFile.NAME, unpacked)
                        || CountryFile.open(unpacked).isEmpty()) {
                    console.accept("MaxMind's answer held no country file; the one there is stays.");
                    return Outcome.FAILED;
                }
                Files.move(unpacked, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return Outcome.FETCHED;
            } finally {
                Files.deleteIfExists(unpacked);
            }
        }
    }
}
