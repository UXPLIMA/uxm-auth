package com.uxplima.auth.domain.network;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.state.AuthMethod;

/**
 * The proxy's word that a player logged in, and how. It names the player by id and name, so it cannot be carried
 * to another player, and it carries the moment it was written, so it cannot be kept.
 *
 * @param nonce a random word, so no two claims read the same
 */
public record Claim(UUID id, String name, AuthMethod method, Instant issued, String nonce) {

    private static final String VERSION = "v1";
    private static final String SEPARATOR = "|";
    private static final int PARTS = 6;

    public Claim {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(issued, "issued");
        Objects.requireNonNull(nonce, "nonce");
        if (name.contains(SEPARATOR) || nonce.contains(SEPARATOR)) {
            throw new IllegalArgumentException("a claim's name and nonce cannot hold " + SEPARATOR);
        }
    }

    /** The text that is signed. */
    String text() {
        return String.join(
                SEPARATOR, VERSION, id.toString(), name, method.name(), Long.toString(issued.toEpochMilli()), nonce);
    }

    /** The claim a signed text names, or empty when the text is no claim of this version. */
    static Optional<Claim> parse(String text) {
        List<String> parts = List.of(text.split("\\|", -1));
        if (parts.size() != PARTS || !parts.get(0).equals(VERSION)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Claim(
                    UUID.fromString(parts.get(1)),
                    parts.get(2),
                    AuthMethod.valueOf(parts.get(3)),
                    Instant.ofEpochMilli(Long.parseLong(parts.get(4))),
                    parts.get(5)));
        } catch (IllegalArgumentException broken) {
            return Optional.empty();
        }
    }
}
