package com.uxplima.auth.adapter.outbound;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmlib.config.HoconConfig;

/**
 * What is told to a Discord channel through its webhook. Nothing goes while the url is empty.
 *
 * @param url the channel's webhook, or empty for none
 * @param server what {@code {server}} reads in a message
 * @param events each event's switch and the operator's message
 */
public record DiscordSettings(String url, String server, Map<Event, Notice> events) {

    /** What may be told, under its key in {@code discord.events}. */
    public enum Event {
        REGISTER("register", "{player} registered on {server} from {address}."),
        LOGIN("login", "{player} logged in on {server} by {method} from {address}."),
        KNOWN_ADDRESS(
                "known-address",
                "{player} is a new account on {address}, which {accounts} accounts have used, on {server}."),
        BAN("ban", "{address} is banned on {server} for {time} after wrong passwords."),
        CONFLICT("conflict", "Staff settled the name {player} on {server}.");

        private final String key;
        private final String shipped;

        Event(String key, String shipped) {
            this.key = key;
            this.shipped = shipped;
        }

        public String key() {
            return key;
        }
    }

    /** One event: whether it is told, and in what words. */
    public record Notice(boolean enabled, String message) {
        public Notice {
            Objects.requireNonNull(message, "message");
        }
    }

    public DiscordSettings {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(server, "server");
        events = Map.copyOf(new EnumMap<>(Objects.requireNonNull(events, "events")));
        if (!url.isBlank() && !webAddress(url)) {
            throw new IllegalArgumentException("discord.url is no http or https address: " + url);
        }
    }

    /** The switch and the words of this event; every event has them. */
    public Notice notice(Event event) {
        return Objects.requireNonNull(events.get(Objects.requireNonNull(event, "event")), "every event is read");
    }

    /** Whether anything is told at all. */
    public boolean on() {
        return !url.isBlank();
    }

    private static boolean webAddress(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost() != null && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()));
        } catch (URISyntaxException notAnAddress) {
            return false;
        }
    }

    static DiscordSettings read(HoconConfig config) {
        Map<Event, Notice> events = new EnumMap<>(Event.class);
        for (Event event : Event.values()) {
            String at = "discord.events." + event.key();
            events.put(
                    event,
                    new Notice(
                            config.getBoolean(at + ".enabled", true),
                            config.getString(at + ".message", event.shipped)));
        }
        return new DiscordSettings(
                config.getString("discord.url", ""), config.getString("discord.server", "server"), events);
    }
}
