package com.uxplima.auth.paper.adapter.inbound.factor;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The setup as a Bedrock form: the key in a field of its own, so a phone can copy it, and a field for the app's
 * first code. Closing the form leaves the setup waiting for {@code /2fa confirm}.
 */
public final class BedrockSetupForm implements FactorSetup.Form {

    /** The fields of the form, by the names their values come back under. */
    static final String KEY = "key";

    static final String CODE = "code";

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final BedrockScreen forms;
    private final Messages messages;

    public BedrockSetupForm(BedrockScreen forms, Messages messages) {
        this.forms = Objects.requireNonNull(forms, "forms");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void show(Player player, String key, int digits, Consumer<String> answer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(answer, "answer");
        forms.sendCustomForm(
                player,
                plain(player, Keys.SCREEN_FACTOR_TITLE),
                plain(player, Keys.SCREEN_FACTOR_BODY),
                List.of(
                        new BedrockWidget.Input(KEY, plain(player, Keys.SCREEN_FACTOR_KEY), "", key),
                        new BedrockWidget.Input(CODE, plain(player, Keys.SCREEN_FACTOR_CODE), "0".repeat(digits), "")),
                values -> answer.accept(values.getOrDefault(CODE, "")),
                () -> {});
    }

    private String plain(Player player, Keys key) {
        return PLAIN.serialize(messages.render(player, key));
    }
}
