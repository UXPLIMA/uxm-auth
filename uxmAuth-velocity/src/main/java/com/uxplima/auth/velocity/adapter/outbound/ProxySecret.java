package com.uxplima.auth.velocity.adapter.outbound;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.uxplima.auth.domain.network.NetworkSecret;

/**
 * The network's secret on the proxy: the one the operator wrote in config.conf, or the one the proxy made on
 * its first start and keeps in a file of its own, for the operator to copy to every server.
 */
public final class ProxySecret {

    /** The file the proxy keeps the secret it made in. */
    public static final String FILE = "network-secret.txt";

    /** The secret, where it is kept, and whether this start made it. */
    public record Loaded(NetworkSecret secret, Path file, boolean created) {
        public Loaded {
            Objects.requireNonNull(secret, "secret");
            Objects.requireNonNull(file, "file");
        }
    }

    private ProxySecret() {}

    /** The configured secret, or the one in the file, or a new one written to the file. */
    public static Loaded loadOrCreate(Path folder, String configured) {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(configured, "configured");
        Path file = folder.resolve(FILE);
        if (!configured.isBlank()) {
            return new Loaded(NetworkSecret.parse(configured), file, false);
        }
        try {
            if (Files.isRegularFile(file)) {
                return new Loaded(NetworkSecret.parse(Files.readString(file, StandardCharsets.UTF_8)), file, false);
            }
            NetworkSecret made = NetworkSecret.generate();
            Files.createDirectories(folder);
            Files.writeString(file, made.encoded() + System.lineSeparator(), StandardCharsets.UTF_8);
            return new Loaded(made, file, true);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Cannot read or write the network secret at " + file, unreadable);
        }
    }
}
