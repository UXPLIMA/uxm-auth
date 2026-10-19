package com.uxplima.auth.paper.adapter.inbound.account;

import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.message.Keys;

/** A Bedrock form with one field, for the password or code that proves an account is the player's own. */
@FunctionalInterface
public interface ProofForm {

    /** No form: chat is all there is. */
    ProofForm NONE = (player, title, field, answer) -> {};

    /** Shows the form; what the player typed comes back to {@code answer}, and a closed form answers nothing. */
    void show(Player player, Keys title, Keys field, Consumer<String> answer);
}
