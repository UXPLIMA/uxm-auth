package com.uxplima.auth.adapter.outbound.premium;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.PremiumAnswer;

/**
 * The web sources the operator may list, each read the way it answers.
 *
 * <p>Only an answer that says so plainly is cracked. A source that says the same thing for a name it does not
 * know and for a failure of its own answers unknown, so the next source is asked rather than a paid name
 * being handed to whoever typed it.
 */
public enum PremiumSource {

    /** Mojang's own profile lookup: a profile, or no content, or not found. */
    MOJANG("mojang", "https://api.mojang.com/users/profiles/minecraft/{name}") {
        @Override
        PremiumAnswer read(int status, String body, PlayerName asked) {
            if (status == NO_CONTENT || status == NOT_FOUND) {
                return new PremiumAnswer.Cracked();
            }
            return status == OK
                    ? object(body)
                            .map(json -> profile(json, "id", "name", asked))
                            .orElse(unknown())
                    : unknown();
        }
    },

    /** PlayerDB: a player under data, or a code that names an unknown name. */
    PLAYERDB("playerdb", "https://playerdb.co/api/player/minecraft/{name}") {
        @Override
        PremiumAnswer read(int status, String body, PlayerName asked) {
            Optional<JsonObject> json = object(body);
            if (json.isEmpty()) {
                return unknown();
            }
            if (status == OK && bool(json.get(), "success")) {
                return child(json.get(), "data")
                        .flatMap(data -> child(data, "player"))
                        .map(player -> profile(player, "id", "username", asked))
                        .orElse(unknown());
            }
            String code = text(json.get(), "code").orElse("");
            return code.endsWith("invalid_username") || code.endsWith("not_found")
                    ? new PremiumAnswer.Cracked()
                    : unknown();
        }
    },

    /** MineTools: an id with status OK. Its error looks the same for an unknown name and a failure. */
    MINETOOLS("minetools", "https://api.minetools.eu/uuid/{name}") {
        @Override
        PremiumAnswer read(int status, String body, PlayerName asked) {
            return status == OK
                    ? object(body)
                            .filter(json -> "OK".equals(text(json, "status").orElse("")))
                            .map(json -> profile(json, "id", "name", asked))
                            .orElse(unknown())
                    : unknown();
        }
    },

    /** Ashcon: a user, or not found. */
    ASHCON("ashcon", "https://api.ashcon.app/mojang/v2/user/{name}") {
        @Override
        PremiumAnswer read(int status, String body, PlayerName asked) {
            if (status == NOT_FOUND) {
                return new PremiumAnswer.Cracked();
            }
            return status == OK
                    ? object(body)
                            .map(json -> profile(json, "uuid", "username", asked))
                            .orElse(unknown())
                    : unknown();
        }
    };

    private static final int OK = 200;
    private static final int NO_CONTENT = 204;
    private static final int NOT_FOUND = 404;

    private final String key;
    private final String shippedAddress;

    PremiumSource(String key, String shippedAddress) {
        this.key = key;
        this.shippedAddress = shippedAddress;
    }

    /** Where this source answers, as the shipped config.conf says. */
    public String shippedAddress() {
        return shippedAddress;
    }

    /** The word config.conf names this source by. */
    public String key() {
        return key;
    }

    /** The source config.conf names by this word, if there is one. */
    public static Optional<PremiumSource> byKey(String written) {
        String wanted = written.trim().toLowerCase(Locale.ROOT);
        for (PremiumSource source : values()) {
            if (source.key.equals(wanted)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }

    /** What this source's answer says about the name it was asked. */
    abstract PremiumAnswer read(int status, String body, PlayerName asked);

    /**
     * A profile with an id and a name. A demo account is no paid one, and an answer about another name, or with
     * an id that cannot be read, is no answer.
     */
    private static PremiumAnswer profile(JsonObject json, String idField, String nameField, PlayerName asked) {
        if (bool(json, "demo")) {
            return new PremiumAnswer.Cracked();
        }
        Optional<String> name = text(json, nameField);
        Optional<UUID> id = text(json, idField).flatMap(PremiumSource::uuid);
        if (name.isEmpty() || id.isEmpty() || !name.get().equalsIgnoreCase(asked.value())) {
            return unknown();
        }
        return new PremiumAnswer.Premium(id.get(), name.get());
    }

    private static Optional<UUID> uuid(String written) {
        String plain = written.replace("-", "");
        if (plain.length() != 32) {
            return Optional.empty();
        }
        try {
            return Optional.of(new UUID(
                    Long.parseUnsignedLong(plain.substring(0, 16), 16),
                    Long.parseUnsignedLong(plain.substring(16), 16)));
        } catch (NumberFormatException broken) {
            return Optional.empty();
        }
    }

    private static Optional<JsonObject> object(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonObject() ? Optional.of(parsed.getAsJsonObject()) : Optional.empty();
        } catch (JsonParseException unreadable) {
            return Optional.empty();
        }
    }

    private static Optional<JsonObject> child(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value != null && value.isJsonObject() ? Optional.of(value.getAsJsonObject()) : Optional.empty();
    }

    private static Optional<String> text(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value != null && value.isJsonPrimitive() ? Optional.of(value.getAsString()) : Optional.empty();
    }

    private static boolean bool(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value != null
                && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isBoolean()
                && value.getAsBoolean();
    }

    private static PremiumAnswer unknown() {
        return new PremiumAnswer.Unknown();
    }
}
