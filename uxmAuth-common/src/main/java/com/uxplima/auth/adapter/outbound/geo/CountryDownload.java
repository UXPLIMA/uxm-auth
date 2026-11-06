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
import java.util.Optional;
import java.util.Set;
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

    /** The answers that send a download elsewhere. */
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

    /** How many times a download is sent on before it is given up: MaxMind sends it once, to its storage. */
    private static final int HOPS = 3;

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

    /**
     * Asks MaxMind with the account, and follows the redirect it answers with to the storage that serves the file.
     * The client follows no redirect itself, and must not: the account and licence go to MaxMind alone, never to the
     * host a redirect names, so each hop is asked again here without them, and only over HTTPS.
     */
    private Outcome fetch(GeoSettings.Licence account, Path target) throws IOException, InterruptedException {
        String basic = Base64.getEncoder()
                .encodeToString((account.accountId() + ":" + account.key()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(from)
                .timeout(TIMEOUT)
                .header("Authorization", "Basic " + basic)
                .GET()
                .build();
        for (int hop = 0; ; hop++) {
            HttpResponse<InputStream> response = web.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                int status = response.statusCode();
                if (status == 200) {
                    return unpack(body, target);
                }
                if (status == 401) {
                    console.accept("MaxMind refused the account and licence key (401), so the country file was not"
                            + " fetched and country rules have nothing to read. Check protection.countries.account-id"
                            + " and licence-key.");
                    return Outcome.FAILED;
                }
                if (!REDIRECTS.contains(status)) {
                    console.accept("Could not fetch the country file from MaxMind: it answered " + status
                            + ". The file there is stays; the next check tries again.");
                    return Outcome.FAILED;
                }
                Optional<URI> next = locationOf(response, request.uri());
                Optional<String> refused = refusal(next, hop);
                if (refused.isPresent()) {
                    console.accept("MaxMind sent the download elsewhere (" + status + ") and it was not followed: "
                            + refused.get() + ". The file there is stays; the next check tries again.");
                    return Outcome.FAILED;
                }
                request = HttpRequest.newBuilder(next.orElseThrow())
                        .timeout(TIMEOUT)
                        .GET()
                        .build();
            }
        }
    }

    /** Where a redirect sends the download, read against the address that answered; nothing when it names none. */
    private static Optional<URI> locationOf(HttpResponse<?> response, URI asked) {
        Optional<String> written = response.headers().firstValue("Location");
        if (written.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(asked.resolve(written.get().trim()));
        } catch (IllegalArgumentException notAnAddress) {
            return Optional.empty();
        }
    }

    /**
     * Why a redirect is not followed, or nothing when it is. The address is never printed: the storage MaxMind sends a
     * download to signs it into the address.
     */
    private static Optional<String> refusal(Optional<URI> next, int hop) {
        if (next.isEmpty()) {
            return Optional.of("it named no address");
        }
        if (hop >= HOPS) {
            return Optional.of("it sent it on more than " + HOPS + " times");
        }
        if (!"https".equalsIgnoreCase(next.get().getScheme()) || next.get().getHost() == null) {
            return Optional.of("the address it named is not HTTPS");
        }
        return Optional.empty();
    }

    private Outcome unpack(InputStream body, Path target) throws IOException {
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
