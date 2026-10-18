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
    private static final String LET_IN = "L";
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
                        text(sent, SESSIONS_ENDED, ended.account(), ended.keep().orElse(""));
                    case Bulletin.LetIn letIn -> text(sent, LET_IN, letIn.account(), "");
                });
    }

    /** Every bulletin travels in the same six parts: the mark, its kind, who sent it, when, its account, and more. */
    private static String text(Sent sent, String kind, UUID account, String more) {
        return String.join(
                SEPARATOR,
                VERSION,
                kind,
                sent.origin().toString(),
                String.valueOf(sent.issued().toEpochMilli()),
                account.toString(),
                more);
    }

    /** The bulletin, when the text is one this network signed and it is fresh; nothing otherwise. */
    public Optional<Sent> open(String sealed, Instant now) {
        Objects.requireNonNull(sealed, "sealed");
        Objects.requireNonNull(now, "now");
        return claims.unseal(sealed).flatMap(Bulletins::parse).filter(read -> claims.fresh(read.issued(), now));
    }

    private static Optional<Sent> parse(String text) {
        List<String> parts = List.of(text.split("\\|", -1));
        if (parts.size() != PARTS || !parts.get(0).equals(VERSION)) {
            return Optional.empty();
        }
        try {
            UUID account = UUID.fromString(parts.get(4));
            String more = parts.get(5);
            Optional<Bulletin> bulletin =
                    switch (parts.get(1)) {
                        case SESSIONS_ENDED ->
                            Optional.of(new Bulletin.SessionsEnded(
                                    account, more.isEmpty() ? Optional.empty() : Optional.of(more)));
                        case LET_IN -> more.isEmpty() ? Optional.of(new Bulletin.LetIn(account)) : Optional.empty();
                        default -> Optional.empty();
                    };
            return bulletin.map(read ->
                    new Sent(read, UUID.fromString(parts.get(2)), Instant.ofEpochMilli(Long.parseLong(parts.get(3)))));
        } catch (IllegalArgumentException unreadable) {
            return Optional.empty();
        }
    }
}
