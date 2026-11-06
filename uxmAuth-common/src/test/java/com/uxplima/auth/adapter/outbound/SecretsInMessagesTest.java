package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** A value that may hold a secret is never repeated in a message or a settings line. */
class SecretsInMessagesTest {

    @Test
    void anAddressIsShownByItsDatabaseAndHostAlone() {
        assertThat(AuthDatabase.shown("jdbc:mysql://auth:s3cret@db.example.net:3306/auth?password=s3cret"))
                .isEqualTo("jdbc:mysql://db.example.net:3306");
        assertThat(AuthDatabase.shown("jdbc:postgresql://db.example.net/auth?user=auth&password=s3cret"))
                .isEqualTo("jdbc:postgresql://db.example.net");
        assertThat(AuthDatabase.shown("jdbc:sqlite:plugins/uxmAuth/data/accounts.db"))
                .isEqualTo("jdbc:sqlite");
        assertThat(AuthDatabase.shown("mysql://auth:s3cret@db")).doesNotContain("s3cret");
    }

    @Test
    void aWrongAddressIsRefusedWithoutRepeatingIt() {
        assertThatThrownBy(() -> AuthDatabase.driverOf("jdbc://auth:s3cret@db.example.net/auth"))
                .hasMessageNotContaining("s3cret");
        assertThatThrownBy(() -> AuthDatabase.driverOf("auth:s3cret@db.example.net"))
                .hasMessageNotContaining("s3cret");
    }

    @Test
    void theStorageSettingsPrintNoPasswordOfTheAddress() {
        assertThat(new PluginSettings.Storage("jdbc:mysql://auth:s3cret@db.example.net/auth", "auth", "s3cret", 4)
                        .toString())
                .doesNotContain("s3cret");
    }

    @Test
    void aWrongWebhookIsRefusedWithoutRepeatingIt() {
        Map<DiscordSettings.Event, DiscordSettings.Notice> events = new EnumMap<>(DiscordSettings.Event.class);
        for (DiscordSettings.Event event : DiscordSettings.Event.values()) {
            events.put(event, new DiscordSettings.Notice(true, "{player}"));
        }

        assertThatThrownBy(() -> new DiscordSettings("discord.com/api/webhooks/1/t0ken", "server", events))
                .hasMessageNotContaining("t0ken");
        assertThat(new DiscordSettings("https://discord.com/api/webhooks/1/t0ken", "server", events).toString())
                .doesNotContain("t0ken");
    }
}
