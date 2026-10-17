package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.password.CharClass;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/** Tells a player why the policy refused a password, in their language. */
final class PasswordWords {

    private PasswordWords() {}

    static void say(Messages messages, Player player, PasswordVerdict verdict) {
        switch (verdict) {
            case PasswordVerdict.Accepted accepted -> {}
            case PasswordVerdict.TooShort tooShort ->
                messages.send(
                        player, Keys.PASSWORD_TOO_SHORT, Text.placeholder("min", String.valueOf(tooShort.minimum())));
            case PasswordVerdict.TooLong tooLong ->
                messages.send(
                        player, Keys.PASSWORD_TOO_LONG, Text.placeholder("max", String.valueOf(tooLong.maximum())));
            case PasswordVerdict.TooLongForTheHash forTheHash ->
                messages.send(player, Keys.PASSWORD_TOO_LONG_FOR_THE_HASH);
            case PasswordVerdict.BadCharacters bad -> messages.send(player, Keys.PASSWORD_BAD_CHARACTERS);
            case PasswordVerdict.Missing missing ->
                messages.send(
                        player, Keys.PASSWORD_MISSING, Text.component("classes", classes(messages, player, missing)));
        }
    }

    private static Component classes(Messages messages, Player player, PasswordVerdict.Missing missing) {
        List<Component> names = new ArrayList<>();
        for (CharClass needed : missing.classes().stream().sorted().toList()) {
            names.add(messages.render(player, keyOf(needed)));
        }
        return Component.join(
                net.kyori.adventure.text.JoinConfiguration.separator(
                        messages.render(player, Keys.PASSWORD_CLASS_SEPARATOR)),
                names);
    }

    private static Keys keyOf(CharClass needed) {
        return switch (needed) {
            case LOWER -> Keys.PASSWORD_CLASS_LOWER;
            case UPPER -> Keys.PASSWORD_CLASS_UPPER;
            case DIGIT -> Keys.PASSWORD_CLASS_DIGIT;
            case SYMBOL -> Keys.PASSWORD_CLASS_SYMBOL;
        };
    }
}
