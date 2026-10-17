package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.uxmlib.text.message.MessageKey;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import org.jspecify.annotations.Nullable;

/**
 * Builds the register and login screens from {@code screens.conf} and {@code config.conf}, in the language of
 * whoever reads them.
 */
public final class AuthScreens {

    /** The action a press of each button comes back as. */
    public static final String REGISTER = "register";

    public static final String LOGIN = "login";
    public static final String CANCEL = "cancel";

    /** The keys the answers come back under. */
    public static final String PASSWORD = "password";

    public static final String CONFIRM = "confirm";
    public static final String ACCEPT = "accept";

    private static final String KEY_MARK = "@";

    private final Supplier<ScreenSettings> screens;
    private final Supplier<AuthSettings.Passwords> passwords;
    private final Messages messages;
    private final Styler styler;

    public AuthScreens(
            Supplier<ScreenSettings> screens,
            Supplier<AuthSettings.Passwords> passwords,
            Messages messages,
            Styler styler) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.styler = Objects.requireNonNull(styler, "styler");
    }

    /** The register screen, with the reason the last answer was refused as its first line. */
    public DialogSpec register(Audience reader, @Nullable Component reason) {
        Objects.requireNonNull(reader, "reader");
        ScreenSettings settings = screens.get();
        ScreenSettings.Screen screen = settings.register();
        List<Component> body = start(reason);
        if (settings.rules().enabled()) {
            settings.rules().lines().forEach(line -> body.add(text(reader, line)));
        }
        screen.body().forEach(line -> body.add(text(reader, line)));
        List<DialogSpec.Input> inputs = new ArrayList<>();
        inputs.add(password(reader, screen, PASSWORD));
        if (passwords.get().confirmOnRegister()) {
            inputs.add(password(reader, screen, CONFIRM));
        }
        if (settings.rules().enabled()) {
            inputs.add(new DialogSpec.Toggle(ACCEPT, text(reader, screen.label(ACCEPT))));
        }
        return new DialogSpec(
                text(reader, screen.title()), body, inputs, buttons(reader, screen, REGISTER), settings.canClose());
    }

    /** The login screen, with the reason the last answer was refused as its first line. */
    public DialogSpec login(Audience reader, @Nullable Component reason) {
        Objects.requireNonNull(reader, "reader");
        ScreenSettings settings = screens.get();
        ScreenSettings.Screen screen = settings.login();
        List<Component> body = start(reason);
        screen.body().forEach(line -> body.add(text(reader, line)));
        return new DialogSpec(
                text(reader, screen.title()),
                body,
                List.of(password(reader, screen, PASSWORD)),
                buttons(reader, screen, LOGIN),
                settings.canClose());
    }

    private static List<Component> start(@Nullable Component reason) {
        List<Component> body = new ArrayList<>();
        if (reason != null) {
            body.add(reason);
        }
        return body;
    }

    private DialogSpec.TextInput password(Audience reader, ScreenSettings.Screen screen, String key) {
        return new DialogSpec.TextInput(
                key, text(reader, screen.label(key)), passwords.get().maxLength());
    }

    private List<DialogSpec.Button> buttons(Audience reader, ScreenSettings.Screen screen, String submit) {
        return List.of(
                new DialogSpec.Button(text(reader, screen.label("submit")), submit),
                new DialogSpec.Button(text(reader, screen.label(CANCEL)), CANCEL));
    }

    /** A line of the catalogue when it starts with @, the operator's own MiniMessage otherwise. */
    Component text(Audience reader, String written) {
        if (written.startsWith(KEY_MARK)) {
            String path = written.substring(KEY_MARK.length());
            return messages.render(reader, MessageKey.of(path, path));
        }
        return MiniMessage.miniMessage().deserialize(styler.tokens(written, messages.localeOf(reader)));
    }
}
