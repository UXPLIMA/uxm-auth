package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.PasswordWords;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.uxmlib.text.Text;
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

    /** The action of saving a new password in place of a weak one. */
    public static final String REPLACE = "replace";

    /** The press that answers the captcha, and the field its code is typed in. */
    public static final String CAPTCHA = "captcha";

    /** The press that answers the code of the second step, typed in the {@link #CODE} field. */
    public static final String FACTOR = "factor";

    /** The longest code the second step takes: an app's code, or a backup code with its dashes. */
    public static final int LONGEST_CODE = 16;

    public static final String CODE = "code";

    /** The action of "this name is mine" on the login screen. */
    public static final String CLAIM = "claim";

    /** The keys the answers come back under. */
    public static final String PASSWORD = "password";

    public static final String CONFIRM = "confirm";
    public static final String ACCEPT = "accept";

    /** The "remember me" box, shown while sessions are by the player's choice. */
    public static final String REMEMBER = "remember";

    /** The pad's digits sit three to a row, as on a telephone. */
    static final int PAD_COLUMNS = 3;

    private static final String KEY_MARK = "@";

    private final Supplier<ScreenSettings> screens;
    private final Supplier<AuthSettings.Passwords> passwords;
    private final Messages messages;
    private final Styler styler;
    private final BooleanSupplier claimable;
    private final BooleanSupplier remembering;

    public AuthScreens(
            Supplier<ScreenSettings> screens,
            Supplier<AuthSettings.Passwords> passwords,
            Messages messages,
            Styler styler) {
        this(screens, passwords, messages, styler, () -> false);
    }

    /** @param claimable whether a name can be claimed here now, which puts "this name is mine" on the login screen */
    public AuthScreens(
            Supplier<ScreenSettings> screens,
            Supplier<AuthSettings.Passwords> passwords,
            Messages messages,
            Styler styler,
            BooleanSupplier claimable) {
        this(screens, passwords, messages, styler, claimable, () -> false);
    }

    /** @param remembering whether sessions are by the player's choice, which puts "remember me" on the screens */
    public AuthScreens(
            Supplier<ScreenSettings> screens,
            Supplier<AuthSettings.Passwords> passwords,
            Messages messages,
            Styler styler,
            BooleanSupplier claimable,
            BooleanSupplier remembering) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.styler = Objects.requireNonNull(styler, "styler");
        this.claimable = Objects.requireNonNull(claimable, "claimable");
        this.remembering = Objects.requireNonNull(remembering, "remembering");
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
        remember(reader, screen).ifPresent(inputs::add);
        return new DialogSpec(
                text(reader, screen.title()), body, inputs, buttons(reader, screen, REGISTER), settings.canClose());
    }

    /**
     * The screen of a player whose right password no longer meets the rules: a new password, typed twice, with
     * why on top. It is typed even on a pad server, where the digits are typed as well.
     */
    public DialogSpec replace(Audience reader, @Nullable Component reason) {
        Objects.requireNonNull(reader, "reader");
        ScreenSettings settings = screens.get();
        ScreenSettings.Screen screen = settings.replace();
        List<Component> body = start(reason);
        screen.body().forEach(line -> body.add(text(reader, line)));
        return new DialogSpec(
                text(reader, screen.title()),
                body,
                List.of(password(reader, screen, PASSWORD), password(reader, screen, CONFIRM)),
                buttons(reader, screen, REPLACE),
                settings.canClose());
    }

    /** The captcha with the code of this showing, and the reason it is asked, or the last answer refused, on top. */
    public DialogSpec captcha(Audience reader, String code, @Nullable Component reason) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(code, "code");
        ScreenSettings settings = screens.get();
        ScreenSettings.Screen screen = settings.captcha();
        List<Component> body = start(reason);
        screen.body().forEach(line -> body.add(text(reader, line, Text.placeholder(CODE, code))));
        return new DialogSpec(
                text(reader, screen.title()),
                body,
                List.of(new DialogSpec.TextInput(CODE, text(reader, screen.label(CODE)), code.length())),
                buttons(reader, screen, CAPTCHA),
                settings.canClose());
    }

    /** The code screen of the second step, with the reason the last code was refused on top. */
    public DialogSpec code(Audience reader, @Nullable Component reason) {
        Objects.requireNonNull(reader, "reader");
        ScreenSettings settings = screens.get();
        ScreenSettings.Screen screen = settings.code();
        List<Component> body = start(reason);
        screen.body().forEach(line -> body.add(text(reader, line)));
        return new DialogSpec(
                text(reader, screen.title()),
                body,
                List.of(new DialogSpec.TextInput(CODE, text(reader, screen.label(CODE)), LONGEST_CODE)),
                buttons(reader, screen, FACTOR),
                settings.canClose());
    }

    /** The replace screen with the reason the old password no longer passes on top. */
    public DialogSpec replace(Audience reader, PasswordVerdict why) {
        Objects.requireNonNull(why, "why");
        return replace(reader, PasswordWords.reason(messages, reader, why).orElse(null));
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
        List<DialogSpec.Input> inputs = new ArrayList<>();
        inputs.add(password(reader, screen, PASSWORD));
        remember(reader, screen).ifPresent(inputs::add);
        return new DialogSpec(
                text(reader, screen.title()), body, inputs, loginButtons(reader, screen), settings.canClose());
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
        List<DialogSpec.Input> inputs = new ArrayList<>();
        if (registering && settings.rules().enabled()) {
            inputs.add(new DialogSpec.Toggle(
                    ACCEPT, text(reader, settings.register().label(ACCEPT))));
        }
        remember(reader, pin).ifPresent(inputs::add);
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

    /**
     * Whether the login screen carries "this name is mine" now: a name can be claimed here, the operator labels
     * the button, and the screen is not the pad. A press is honoured only while this holds, whatever a client
     * sends.
     */
    public boolean offersClaim() {
        ScreenSettings settings = screens.get();
        return claimable.getAsBoolean()
                && !settings.pinPad()
                && !settings.login().label(CLAIM).isEmpty();
    }

    /** Whether the screens ask "remember me" now; a tick is honoured only while this holds. */
    public boolean asksToRemember() {
        return remembering.getAsBoolean();
    }

    /** The "remember me" box, while the screens ask it and the operator labels it on this screen. */
    private Optional<DialogSpec.Input> remember(Audience reader, ScreenSettings.Screen screen) {
        String label = screen.label(REMEMBER);
        if (!asksToRemember() || label.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DialogSpec.Toggle(REMEMBER, text(reader, label)));
    }

    /** Log in, "this name is mine" while the screen offers it, and leave. */
    private List<DialogSpec.Button> loginButtons(Audience reader, ScreenSettings.Screen screen) {
        String claim = screen.label(CLAIM);
        if (!offersClaim()) {
            return buttons(reader, screen, LOGIN);
        }
        return List.of(
                new DialogSpec.Button(text(reader, screen.label("submit")), LOGIN),
                new DialogSpec.Button(text(reader, claim), CLAIM),
                new DialogSpec.Button(text(reader, screen.label(CANCEL)), CANCEL));
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
