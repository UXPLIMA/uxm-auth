package com.uxplima.auth.domain.network;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.state.AuthMethod;

/**
 * The proxy's word about a player: logged in, and how, or still waiting for a password with what the door
 * decided. It names the player by id and name, so it cannot be carried to another player, the server it was
 * written for, so it cannot be carried to another server, and the moment it was written, so it cannot be kept.
 *
 * <p>The same words travel the other way as a server's report to the proxy. Which way they travel is part of the
 * signed text and picks the key they are signed with, so a report never reads as a claim or a claim as a report.
 *
 * @param server the server the claim was written for, or the server that wrote the report, in lower case
 * @param nonce a random word, so no two claims read the same and a copy of one is noticed
 */
public record Claim(UUID id, String name, String server, Standing standing, Instant issued, String nonce) {

    /** Which way the words travel. */
    public enum Direction {
        /** The proxy's claim, written before a connection to a server. */
        TO_SERVER("claim"),
        /** A server's report, written for the proxy about a player on that server. */
        TO_PROXY("report");

        private final String tag;

        Direction(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

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

    /**
     * The first step passed, by this method, and the app's code is owed: the server asks the code, never the
     * password.
     */
    public record Code(AuthMethod method) implements Standing {
        public Code {
            Objects.requireNonNull(method, "method");
        }
    }

    private static final String VERSION = "v2";

    /** How the text of a claim of the release before servers and directions were signed opens. */
    static final String OUTDATED = "v1|";

    private static final String SEPARATOR = "|";
    private static final int PARTS = 8;
    private static final String LOGGED_IN = "L:";
    private static final String WAITING = "W:";
    private static final String CODE = "C:";

    public Claim {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        server = Objects.requireNonNull(server, "server").strip().toLowerCase(Locale.ROOT);
        Objects.requireNonNull(standing, "standing");
        Objects.requireNonNull(issued, "issued");
        Objects.requireNonNull(nonce, "nonce");
        if (name.contains(SEPARATOR) || server.contains(SEPARATOR) || nonce.contains(SEPARATOR)) {
            throw new IllegalArgumentException("a claim's name, server and nonce cannot hold " + SEPARATOR);
        }
        if (server.isEmpty()) {
            throw new IllegalArgumentException("a claim names the server it is written for, or by");
        }
    }

    /** A claim that the player logged in by this method. */
    public Claim(UUID id, String name, String server, AuthMethod method, Instant issued, String nonce) {
        this(id, name, server, new LoggedIn(method), issued, nonce);
    }

    /** Whether the claim was written for, or by, the server of this name; a name is read without its case. */
    public boolean names(String other) {
        return server.equals(Objects.requireNonNull(other, "other").strip().toLowerCase(Locale.ROOT));
    }

    /** The text that is signed, for the way it travels. */
    String text(Direction direction) {
        return String.join(
                SEPARATOR,
                VERSION,
                Objects.requireNonNull(direction, "direction").tag(),
                server,
                id.toString(),
                name,
                standingText(),
                Long.toString(issued.toEpochMilli()),
                nonce);
    }

    private String standingText() {
        return switch (standing) {
            case LoggedIn in -> LOGGED_IN + in.method().name();
            case Waiting waiting ->
                WAITING + flag(waiting.unverified()) + flag(waiting.explain()) + flag(waiting.marked());
            case Code code -> CODE + code.method().name();
        };
    }

    private static String flag(boolean on) {
        return on ? "1" : "0";
    }

    /** The claim a signed text names, or empty when the text is no claim of this version or travels the other way. */
    static Optional<Claim> parse(String text, Direction direction) {
        List<String> parts = List.of(text.split("\\|", -1));
        if (parts.size() != PARTS
                || !parts.get(0).equals(VERSION)
                || !parts.get(1).equals(direction.tag())) {
            return Optional.empty();
        }
        try {
            return standingOf(parts.get(5))
                    .map(standing -> new Claim(
                            UUID.fromString(parts.get(3)),
                            parts.get(4),
                            parts.get(2),
                            standing,
                            Instant.ofEpochMilli(Long.parseLong(parts.get(6))),
                            parts.get(7)));
        } catch (IllegalArgumentException broken) {
            return Optional.empty();
        }
    }

    private static Optional<Standing> standingOf(String written) {
        if (written.startsWith(LOGGED_IN)) {
            return Optional.of(new LoggedIn(AuthMethod.valueOf(written.substring(LOGGED_IN.length()))));
        }
        if (written.startsWith(CODE)) {
            return Optional.of(new Code(AuthMethod.valueOf(written.substring(CODE.length()))));
        }
        String flags = written.startsWith(WAITING) ? written.substring(WAITING.length()) : "";
        if (!flags.matches("[01]{3}")) {
            return Optional.empty();
        }
        return Optional.of(new Waiting(flags.charAt(0) == '1', flags.charAt(1) == '1', flags.charAt(2) == '1'));
    }
}
