package com.uxplima.auth.paper.adapter.inbound.account;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import com.uxplima.uxmlib.text.message.Messages;

/** The backup password in a Floodgate form, in a field filled with it so the player can copy it. */
public final class BedrockBackupForm implements BiConsumer<Player, String> {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final BedrockScreen forms;
    private final Messages messages;

    public BedrockBackupForm(BedrockScreen forms, Messages messages) {
        this.forms = Objects.requireNonNull(forms, "forms");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void accept(Player player, String password) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(password, "password");
        forms.sendCustomForm(
                player,
                PLAIN.serialize(messages.render(player, Keys.ACCOUNT_BACKUP_FORM_TITLE)),
                null,
                List.of(new BedrockWidget.Input(
                        "password",
                        PLAIN.serialize(messages.render(player, Keys.ACCOUNT_BACKUP_FORM_FIELD)),
                        "",
                        password)),
                values -> {},
                () -> {});
    }
}
