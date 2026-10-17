package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.application.message.Keys;
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

    /** The pad's digits sit three to a row, as on a telephone. */
    static final int PAD_COLUMNS = 3;

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
        if (settings.pinPad()) {
            return pad(reader, reason, 0, false, true);
        }
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
        if (settings.pinPad()) {
            return pad(reader, reason, 0, false, false);
        }
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

    /**
     * The pad: the digits in rows of three, erase and enter beside the zero, and a line under it that masks
     * every digit pressed so far. A registration carries the rules box while the rules are on.
     */
    public DialogSpec pad(
            Audience reader, @Nullable Component reason, int entered, boolean repeating, boolean registering) {
        Objects.requireNonNull(reader, "reader");
        ScreenSettings settings = screens.get();
        ScreenSettings.Screen pin = settings.pin();
        List<Component> body = start(reason);
        if (registering && settings.rules().enabled()) {
            settings.rules().lines().forEach(line -> body.add(text(reader, line)));
        }
        pin.body().forEach(line -> body.add(text(reader, line)));
        if (repeating) {
            body.add(text(reader, pin.label("repeat")));
        }
        Component mask = text(reader, pin.label("mask"));
        Component code = Component.join(JoinConfiguration.noSeparators(), Collections.nCopies(entered, mask));
        body.add(text(reader, pin.label("entered"), Placeholder.component("code", code)));
        List<DialogSpec.Input> inputs = registering && settings.rules().enabled()
                ? List.of(new DialogSpec.Toggle(
                        ACCEPT, text(reader, settings.register().label(ACCEPT))))
                : List.of();
        return new DialogSpec(
                text(reader, pin.title()), body, inputs, padButtons(reader, pin), settings.canClose(), PAD_COLUMNS);
    }

    private List<DialogSpec.Button> padButtons(Audience reader, ScreenSettings.Screen pin) {
        List<DialogSpec.Button> buttons = new ArrayList<>();
        for (int digit = 1; digit <= 9; digit++) {
            buttons.add(new DialogSpec.Button(Component.text(digit), PinPad.digit(digit)));
        }
        buttons.add(new DialogSpec.Button(text(reader, pin.label("erase")), PinPad.ERASE));
        buttons.add(new DialogSpec.Button(Component.text(0), PinPad.digit(0)));
        buttons.add(new DialogSpec.Button(text(reader, pin.label("enter")), PinPad.ENTER));
        buttons.add(new DialogSpec.Button(text(reader, pin.label(CANCEL)), CANCEL));
        return buttons;
    }

    /**
     * The rules as lines of chat, for a client that cannot show the register screen, and the line that says
     * registering with the command accepts them. Empty while the rules are off.
     */
    public List<Component> rulesInChat(Audience reader) {
        Objects.requireNonNull(reader, "reader");
        ScreenSettings.Rules rules = screens.get().rules();
        if (!rules.enabled()) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        rules.lines().forEach(line -> lines.add(text(reader, line)));
        lines.add(messages.render(reader, Keys.RULES_ACCEPTED_BY_REGISTERING));
        return lines;
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
    Component text(Audience reader, String written, TagResolver... resolvers) {
        if (written.startsWith(KEY_MARK)) {
            String path = written.substring(KEY_MARK.length());
            return messages.render(reader, MessageKey.of(path, path), resolvers);
        }
        return MiniMessage.miniMessage()
                .deserialize(styler.tokens(written, messages.localeOf(reader)), TagResolver.resolver(resolvers));
    }
}
