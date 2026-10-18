package com.uxplima.auth.paper.adapter.inbound.mail;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import com.uxplima.uxmlib.text.message.Messages;

/** {@code /email} on Bedrock: a form for the address, then a form for the code it got. Closing either asks nothing. */
public final class BedrockEmailForms implements EmailLink.Forms {

    /** The fields of the forms, by the names their values come back under. */
    static final String ADDRESS = "address";

    static final String CODE = "code";

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final BedrockScreen forms;
    private final Messages messages;

    public BedrockEmailForms(BedrockScreen forms, Messages messages) {
        this.forms = Objects.requireNonNull(forms, "forms");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void address(Player player, Consumer<String> answer) {
        show(player, Keys.SCREEN_EMAIL_BODY, ADDRESS, Keys.SCREEN_EMAIL_ADDRESS, answer);
    }

    @Override
    public void code(Player player, Consumer<String> answer) {
        show(player, Keys.SCREEN_EMAIL_CODE_BODY, CODE, Keys.SCREEN_EMAIL_CODE, answer);
    }

    private void show(Player player, Keys body, String field, Keys label, Consumer<String> answer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(answer, "answer");
        forms.sendCustomForm(
                player,
                plain(player, Keys.SCREEN_EMAIL_TITLE),
                plain(player, body),
                List.of(new BedrockWidget.Input(field, plain(player, label), "", "")),
                values -> {
                    String typed = values.getOrDefault(field, "").strip();
                    if (!typed.isEmpty()) {
                        answer.accept(typed);
                    }
                },
                () -> {});
    }

    private String plain(Player player, Keys key) {
        return PLAIN.serialize(messages.render(player, key));
    }
}
