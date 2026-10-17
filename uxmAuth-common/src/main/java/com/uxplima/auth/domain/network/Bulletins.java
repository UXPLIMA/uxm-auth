package com.uxplima.auth.domain.network;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Seals a bulletin with the network's secret, and opens one only signed and fresh.
 *
 * <p>A bulletin's text opens with its own version mark, which no claim's text carries, so the one secret that
 * signs both can never make a claim pass for a bulletin or a bulletin for a claim.
 */
public final class Bulletins {

    /**
     * A bulletin as it travels: who sent it, by a random id each member takes at its start so it can tell its
     * own when it comes back, and when.
     */
    public record Sent(Bulletin bulletin, UUID origin, Instant issued) {
        public Sent {
            Objects.requireNonNull(bulletin, "bulletin");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(issued, "issued");
        }
    }

    private static final String VERSION = "b1";
    private static final String SEPARATOR = "|";
    private static final String SESSIONS_ENDED = "S";
    private static final int PARTS = 6;

    private final Claims claims;

    public Bulletins(Claims claims) {
        this.claims = Objects.requireNonNull(claims, "claims");
    }

    public String seal(Sent sent) {
        Objects.requireNonNull(sent, "sent");
        return claims.seal(
                switch (sent.bulletin()) {
                    case Bulletin.SessionsEnded ended ->
                        String.join(
                                SEPARATOR,
                                VERSION,
                                SESSIONS_ENDED,
                                sent.origin().toString(),
                                String.valueOf(sent.issued().toEpochMilli()),
                                ended.account().toString(),
                                ended.keep().orElse(""));
                });
    }

    /** The bulletin, when the text is one this network signed and it is fresh; nothing otherwise. */
    public Optional<Sent> open(String sealed, Instant now) {
        Objects.requireNonNull(sealed, "sealed");
        Objects.requireNonNull(now, "now");
        return claims.unseal(sealed).flatMap(Bulletins::parse).filter(read -> claims.fresh(read.issued(), now));
    }

    private static Optional<Sent> parse(String text) {
        List<String> parts = List.of(text.split("\\|", -1));
        if (parts.size() != PARTS
                || !parts.get(0).equals(VERSION)
                || !parts.get(1).equals(SESSIONS_ENDED)) {
            return Optional.empty();
        }
        String keep = parts.get(5);
        try {
            return Optional.of(new Sent(
                    new Bulletin.SessionsEnded(
                            UUID.fromString(parts.get(4)), keep.isEmpty() ? Optional.empty() : Optional.of(keep)),
                    UUID.fromString(parts.get(2)),
                    Instant.ofEpochMilli(Long.parseLong(parts.get(3)))));
        } catch (IllegalArgumentException unreadable) {
            return Optional.empty();
        }
    }
}
