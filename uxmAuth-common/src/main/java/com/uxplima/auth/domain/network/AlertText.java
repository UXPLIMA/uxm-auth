package com.uxplima.auth.domain.network;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.uxplima.auth.domain.name.PlayerName;

/**
 * How an alert rides in the last part of a bulletin: its kind, then its fields, each escaped so a name with a
 * separator in it cannot split the frame.
 */
final class AlertText {

    private static final String SEPARATOR = ";";
    private static final String FAILED = "F";
    private static final String BANNED = "B";
    private static final String SHARED = "S";
    private static final String STAFF = "A";

    private AlertText() {}

    static String write(Bulletin.Alert alert) {
        return switch (alert) {
            case Bulletin.FailedLogin failed -> join(FAILED, failed.address());
            case Bulletin.Banned banned ->
                join(BANNED, banned.address(), String.valueOf(banned.length().toSeconds()));
            case Bulletin.SharedAddress shared ->
                join(SHARED, shared.name().value(), shared.address(), String.valueOf(shared.accounts()));
            case Bulletin.StaffActed acted ->
                join(STAFF, acted.actor().map(PlayerName::value).orElse(""), acted.action(), acted.target());
        };
    }

    private static String join(String kind, String... fields) {
        return String.join(
                SEPARATOR,
                Stream.concat(
                                Stream.of(kind),
                                Stream.of(fields).map(field -> URLEncoder.encode(field, StandardCharsets.UTF_8)))
                        .toList());
    }

    /** The alert, or nothing when the kind is unknown or a part is missing or unreadable. */
    static Optional<Bulletin> read(String more) {
        List<String> parts = Stream.of(more.split(SEPARATOR, -1))
                .map(part -> URLDecoder.decode(part, StandardCharsets.UTF_8))
                .toList();
        List<String> fields = parts.subList(1, parts.size());
        return switch (parts.get(0)) {
            case FAILED -> fields.size() == 1 ? Optional.of(new Bulletin.FailedLogin(fields.get(0))) : Optional.empty();
            case BANNED ->
                fields.size() == 2
                        ? Optional.of(
                                new Bulletin.Banned(fields.get(0), Duration.ofSeconds(Long.parseLong(fields.get(1)))))
                        : Optional.empty();
            case SHARED -> fields.size() == 3 ? Optional.of(shared(fields)) : Optional.empty();
            case STAFF -> fields.size() == 3 ? Optional.of(staff(fields)) : Optional.empty();
            default -> Optional.empty();
        };
    }

    private static Bulletin shared(List<String> fields) {
        return new Bulletin.SharedAddress(
                new PlayerName(fields.get(0)), fields.get(1), Integer.parseInt(fields.get(2)));
    }

    private static Bulletin staff(List<String> fields) {
        Optional<PlayerName> actor =
                fields.get(0).isEmpty() ? Optional.empty() : Optional.of(new PlayerName(fields.get(0)));
        return new Bulletin.StaffActed(actor, fields.get(1), fields.get(2));
    }
}
