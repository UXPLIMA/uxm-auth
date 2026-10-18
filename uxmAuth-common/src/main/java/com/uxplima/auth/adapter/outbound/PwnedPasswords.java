package com.uxplima.auth.adapter.outbound;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.uxplima.auth.application.port.BreachedPasswords;
import com.uxplima.auth.domain.password.Password;

/**
 * haveibeenpwned's range API. Only the first five characters of the password's SHA-1 leave the server, with
 * padding asked for so the answer's size says nothing either; the rest of the hash is matched here. A service
 * that does not answer lets the password through and says so once.
 */
public final class PwnedPasswords implements BreachedPasswords {

    /** Where haveibeenpwned answers a range. */
    public static final URI SERVICE = URI.create("https://api.pwnedpasswords.com/range/");

    private static final int PREFIX = 5;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient web;
    private final URI service;
    private final BooleanSupplier on;
    private final Consumer<String> console;
    private final AtomicBoolean toldDown = new AtomicBoolean();

    public PwnedPasswords(HttpClient web, URI service, BooleanSupplier on, Consumer<String> console) {
        this.web = Objects.requireNonNull(web, "web");
        this.service = Objects.requireNonNull(service, "service");
        this.on = Objects.requireNonNull(on, "on");
        this.console = Objects.requireNonNull(console, "console");
    }

    @Override
    public boolean breached(Password password) {
        Objects.requireNonNull(password, "password");
        if (!on.getAsBoolean()) {
            return false;
        }
        String hash = sha1(password);
        try {
            HttpRequest request = HttpRequest.newBuilder(service.resolve(hash.substring(0, PREFIX)))
                    .timeout(TIMEOUT)
                    .header("Add-Padding", "true")
                    .header("User-Agent", "uxmAuth")
                    .GET()
                    .build();
            HttpResponse<String> answer = web.send(request, HttpResponse.BodyHandlers.ofString());
            if (answer.statusCode() != 200) {
                return down("it answered " + answer.statusCode());
            }
            return listed(answer.body(), hash.substring(PREFIX));
        } catch (IOException failed) {
            return down(failed.getClass().getSimpleName());
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Whether a line of the answer names this suffix with a count; a padded line counts nothing. */
    private static boolean listed(String body, String suffix) {
        return body.lines().anyMatch(line -> {
            int colon = line.indexOf(':');
            return colon > 0
                    && line.substring(0, colon).strip().equalsIgnoreCase(suffix)
                    && !line.substring(colon + 1).strip().equals("0");
        });
    }

    private boolean down(String why) {
        if (toldDown.compareAndSet(false, true)) {
            console.accept("haveibeenpwned could not be asked (" + why + "); new passwords are let through until it"
                    + " answers again.");
        }
        return false;
    }

    private static String sha1(Password password) {
        byte[] bytes = password.utf8();
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-1").digest(bytes))
                    .toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("Every Java runtime carries SHA-1", missing);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
