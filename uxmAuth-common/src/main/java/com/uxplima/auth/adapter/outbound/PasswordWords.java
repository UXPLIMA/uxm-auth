package com.uxplima.auth.adapter.outbound;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.password.CharClass;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/** Why the policy refused a password, in the reader's language, on a server and on the proxy alike. */
public final class PasswordWords {

    private PasswordWords() {}

    /** The line that says why, or nothing for a password that was accepted. */
    public static java.util.Optional<Component> reason(Messages messages, Audience reader, PasswordVerdict verdict) {
        return java.util.Optional.ofNullable(
                switch (verdict) {
                    case PasswordVerdict.Accepted accepted -> null;
                    case PasswordVerdict.TooShort tooShort ->
                        messages.render(
                                reader,
                                Keys.PASSWORD_TOO_SHORT,
                                Text.placeholder("min", String.valueOf(tooShort.minimum())));
                    case PasswordVerdict.TooLong tooLong ->
                        messages.render(
                                reader,
                                Keys.PASSWORD_TOO_LONG,
                                Text.placeholder("max", String.valueOf(tooLong.maximum())));
                    case PasswordVerdict.TooLongForTheHash forTheHash ->
                        messages.render(reader, Keys.PASSWORD_TOO_LONG_FOR_THE_HASH);
                    case PasswordVerdict.BadCharacters bad -> messages.render(reader, Keys.PASSWORD_BAD_CHARACTERS);
                    case PasswordVerdict.Missing missing ->
                        messages.render(
                                reader,
                                Keys.PASSWORD_MISSING,
                                Text.component("classes", classes(messages, reader, missing)));
                });
    }

    private static Component classes(Messages messages, Audience player, PasswordVerdict.Missing missing) {
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
