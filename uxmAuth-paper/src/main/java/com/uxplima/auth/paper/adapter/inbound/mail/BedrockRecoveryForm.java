package com.uxplima.auth.paper.adapter.inbound.mail;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import com.uxplima.uxmlib.text.message.Messages;

/** {@code /recover} on Bedrock: one form for the mailed code and the new password. Closing it asks nothing. */
public final class BedrockRecoveryForm implements Recovery.Form {

    /** The fields of the form, by the names their values come back under. */
    static final String CODE = "code";

    static final String PASSWORD = "password";

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final BedrockScreen forms;
    private final Messages messages;

    public BedrockRecoveryForm(BedrockScreen forms, Messages messages) {
        this.forms = Objects.requireNonNull(forms, "forms");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void show(Player player, BiConsumer<String, String> answer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(answer, "answer");
        forms.sendCustomForm(
                player,
                plain(player, Keys.SCREEN_RECOVER_TITLE),
                plain(player, Keys.SCREEN_RECOVER_BODY),
                List.of(
                        new BedrockWidget.Input(CODE, plain(player, Keys.SCREEN_RECOVER_CODE), "", ""),
                        new BedrockWidget.Input(PASSWORD, plain(player, Keys.SCREEN_RECOVER_PASSWORD), "", "")),
                values -> {
                    String code = values.getOrDefault(CODE, "").strip();
                    String fresh = values.getOrDefault(PASSWORD, "");
                    if (!code.isEmpty() && !fresh.isEmpty()) {
                        answer.accept(code, fresh);
                    }
                },
                () -> {});
    }

    private String plain(Player player, Keys key) {
        return PLAIN.serialize(messages.render(player, key));
    }
}
