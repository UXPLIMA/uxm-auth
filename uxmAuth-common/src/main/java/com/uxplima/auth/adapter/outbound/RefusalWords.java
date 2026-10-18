package com.uxplima.auth.adapter.outbound;

import java.util.Objects;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * What a player reads when another plugin refused their login or registration: that plugin's reason, as it wrote
 * it, or our own words when it gave none. The reason is plain text, never MiniMessage, so another plugin cannot
 * dress its line as ours.
 */
public final class RefusalWords {

    private RefusalWords() {}

    public static Component of(Messages messages, Audience reader, String reason, Keys fallback) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(fallback, "fallback");
        return Objects.requireNonNull(reason, "reason").isBlank()
                ? messages.render(reader, fallback)
                : Component.text(reason);
    }
}
