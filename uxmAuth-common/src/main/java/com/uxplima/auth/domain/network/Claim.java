package com.uxplima.auth.domain.network;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.state.AuthMethod;

/**
 * The proxy's word about a player: logged in, and how, or still waiting for a password with what the door
 * decided. It names the player by id and name, so it cannot be carried to another player, and it carries the
 * moment it was written, so it cannot be kept.
 *
 * @param nonce a random word, so no two claims read the same
 */
public record Claim(UUID id, String name, Standing standing, Instant issued, String nonce) {

    /** Where the player stands with the proxy. */
    public sealed interface Standing {}

    /** Logged in, by this method. */
    public record LoggedIn(AuthMethod method) implements Standing {
        public LoggedIn {
            Objects.requireNonNull(method, "method");
        }
    }

    /**
     * Not logged in: the server asks for the password. The flags are the door's: the account is unverified, the
     * player is told why their last try failed, and the name carries the domain rule's mark.
     */
    public record Waiting(boolean unverified, boolean explain, boolean marked) implements Standing {}

    private static final String VERSION = "v1";
    private static final String SEPARATOR = "|";
    private static final int PARTS = 6;
    private static final String LOGGED_IN = "L:";
    private static final String WAITING = "W:";

    public Claim {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(standing, "standing");
        Objects.requireNonNull(issued, "issued");
        Objects.requireNonNull(nonce, "nonce");
        if (name.contains(SEPARATOR) || nonce.contains(SEPARATOR)) {
            throw new IllegalArgumentException("a claim's name and nonce cannot hold " + SEPARATOR);
        }
    }

    /** A claim that the player logged in by this method. */
    public Claim(UUID id, String name, AuthMethod method, Instant issued, String nonce) {
        this(id, name, new LoggedIn(method), issued, nonce);
    }

    /** The text that is signed. */
    String text() {
        return String.join(
                SEPARATOR, VERSION, id.toString(), name, standingText(), Long.toString(issued.toEpochMilli()), nonce);
    }

    private String standingText() {
        return switch (standing) {
            case LoggedIn in -> LOGGED_IN + in.method().name();
            case Waiting waiting ->
                WAITING + flag(waiting.unverified()) + flag(waiting.explain()) + flag(waiting.marked());
        };
    }

    private static String flag(boolean on) {
        return on ? "1" : "0";
    }

    /** The claim a signed text names, or empty when the text is no claim of this version. */
    static Optional<Claim> parse(String text) {
        List<String> parts = List.of(text.split("\\|", -1));
        if (parts.size() != PARTS || !parts.get(0).equals(VERSION)) {
            return Optional.empty();
        }
        try {
            return standingOf(parts.get(3))
                    .map(standing -> new Claim(
                            UUID.fromString(parts.get(1)),
                            parts.get(2),
                            standing,
                            Instant.ofEpochMilli(Long.parseLong(parts.get(4))),
                            parts.get(5)));
        } catch (IllegalArgumentException broken) {
            return Optional.empty();
        }
    }

    private static Optional<Standing> standingOf(String written) {
        if (written.startsWith(LOGGED_IN)) {
            return Optional.of(new LoggedIn(AuthMethod.valueOf(written.substring(LOGGED_IN.length()))));
        }
        String flags = written.startsWith(WAITING) ? written.substring(WAITING.length()) : "";
        if (!flags.matches("[01]{3}")) {
            return Optional.empty();
        }
        return Optional.of(new Waiting(flags.charAt(0) == '1', flags.charAt(1) == '1', flags.charAt(2) == '1'));
    }
}
