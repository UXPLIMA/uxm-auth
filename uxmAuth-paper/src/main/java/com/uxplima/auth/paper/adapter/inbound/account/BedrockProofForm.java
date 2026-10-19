package com.uxplima.auth.paper.adapter.inbound.account;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import com.uxplima.uxmlib.text.message.Messages;

/** The proof form on Floodgate: a title and one field. An empty answer, or a closed form, asks nothing. */
public final class BedrockProofForm implements ProofForm {

    /** The field's name, which its value comes back under. */
    static final String PROOF = "proof";

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final BedrockScreen forms;
    private final Messages messages;

    public BedrockProofForm(BedrockScreen forms, Messages messages) {
        this.forms = Objects.requireNonNull(forms, "forms");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void show(Player player, Keys title, Keys field, Consumer<String> answer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(answer, "answer");
        forms.sendCustomForm(
                player,
                PLAIN.serialize(messages.render(player, title)),
                null,
                List.of(new BedrockWidget.Input(PROOF, PLAIN.serialize(messages.render(player, field)), "", "")),
                values -> {
                    String typed = values.getOrDefault(PROOF, "");
                    if (!typed.isBlank()) {
                        answer.accept(typed);
                    }
                },
                () -> {});
    }
}
