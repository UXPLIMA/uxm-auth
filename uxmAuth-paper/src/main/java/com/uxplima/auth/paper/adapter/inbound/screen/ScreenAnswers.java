package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.PasswordWords;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.adapter.outbound.TimeWords;
import com.uxplima.auth.application.ChangePassword;
import com.uxplima.auth.application.ChangeResult;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.RegisterAccount;
import com.uxplima.auth.application.RegisterResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.premium.ClaimName;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * What a press on a screen does, for a player in the world or a client still connecting: the same use cases as
 * the commands, with a refusal shown on the same screen and the reason on top.
 */
public final class ScreenAnswers {

    /** What the player filled in, read by the key each field came back under. */
    public interface Answer {
        @Nullable String text(String key);

        boolean ticked(String key);
    }

    private static final String LOGIN_PERMISSION = "uxmauth.login";
    private static final String REGISTER_PERMISSION = "uxmauth.register";
    private static final String CLAIM_PERMISSION = "uxmauth.claim";

    private final AuthScreens screens;
    private final RegisterAccount register;
    private final LogIn logIn;
    private final Supplier<ScreenSettings> settings;
    private final Supplier<AuthSettings.Passwords> passwords;
    private final Messages messages;
    private final ScreenShower shower;
    private final Predicate<UUID> registering;
    private final Claims claims;
    private final Choices choices;
    private final Replacer replacer;
    private final TimeWords times;
    private final PinPad pad = new PinPad();

    /** What the player ticked in the "remember me" box, kept with the session of this connection. */
    @FunctionalInterface
    public interface Choices {
        void chose(UUID connection, boolean remember);
    }

    /** A new password in place of a weak one, for the connection with this id. */
    @FunctionalInterface
    private interface Replacer {
        CompletableFuture<ChangeResult> replace(UUID connection, Password fresh);
    }

    /** "This name is mine" for the connection with this id, made from this address. */
    @FunctionalInterface
    public interface Claims {
        ClaimName.Result claim(UUID connection, String address);
    }

    /** @param registering whether the session under this id waits for an account rather than a password */
    public ScreenAnswers(
            AuthScreens screens,
            RegisterAccount register,
            LogIn logIn,
            Supplier<ScreenSettings> settings,
            Supplier<AuthSettings.Passwords> passwords,
            Messages messages,
            ScreenShower shower,
            Predicate<UUID> registering) {
        this(
                screens,
                register,
                logIn,
                settings,
                passwords,
                messages,
                shower,
                registering,
                (id, address) -> ClaimName.Result.NOT_THIS_RULE);
    }

    /** @param claims what "this name is mine" does, on a server that can prove names */
    public ScreenAnswers(
            AuthScreens screens,
            RegisterAccount register,
            LogIn logIn,
            Supplier<ScreenSettings> settings,
            Supplier<AuthSettings.Passwords> passwords,
            Messages messages,
            ScreenShower shower,
            Predicate<UUID> registering,
            Claims claims) {
        this(screens, register, logIn, settings, passwords, messages, shower, registering, claims, (id, chose) -> {});
    }

    /** @param choices where a tick of "remember me" is kept until the login it came with is written */
    public ScreenAnswers(
            AuthScreens screens,
            RegisterAccount register,
            LogIn logIn,
            Supplier<ScreenSettings> settings,
            Supplier<AuthSettings.Passwords> passwords,
            Messages messages,
            ScreenShower shower,
            Predicate<UUID> registering,
            Claims claims,
            Choices choices) {
        this(screens, register, logIn, settings, passwords, messages, shower, registering, claims, choices, (Replacer)
                (id, fresh) -> CompletableFuture.completedFuture(new ChangeResult.NotLoggedIn()));
    }

    /**
     * @param change what saves a new password in place of a weak one. The screen asks no old password, so only
     *     its replacement of a weak one is reachable from here, never a change of a sound one.
     */
    public ScreenAnswers(
            AuthScreens screens,
            RegisterAccount register,
            LogIn logIn,
            Supplier<ScreenSettings> settings,
            Supplier<AuthSettings.Passwords> passwords,
            Messages messages,
            ScreenShower shower,
            Predicate<UUID> registering,
            Claims claims,
            Choices choices,
            ChangePassword change) {
        this(screens, register, logIn, settings, passwords, messages, shower, registering, claims, choices, (Replacer)
                Objects.requireNonNull(change, "change")::replaceWeak);
    }

    private ScreenAnswers(
            AuthScreens screens,
            RegisterAccount register,
            LogIn logIn,
            Supplier<ScreenSettings> settings,
            Supplier<AuthSettings.Passwords> passwords,
            Messages messages,
            ScreenShower shower,
            Predicate<UUID> registering,
            Claims claims,
            Choices choices,
            Replacer replacer) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.register = Objects.requireNonNull(register, "register");
        this.logIn = Objects.requireNonNull(logIn, "logIn");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.shower = Objects.requireNonNull(shower, "shower");
        this.registering = Objects.requireNonNull(registering, "registering");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.choices = Objects.requireNonNull(choices, "choices");
        this.replacer = Objects.requireNonNull(replacer, "replacer");
        this.times = new TimeWords(messages);
    }

    /** The pad's digits, for whoever ends a connection to drop them. */
    PinPad pinPad() {
        return pad;
    }

    /** Drops anything a connection pressed and did not hand over. */
    public void forget(UUID connection) {
        pad.forget(connection);
    }

    /** Runs one press, answering when the guest has been shown the outcome. */
    public CompletableFuture<Void> answer(Guest guest, String action, Answer answer) {
        Objects.requireNonNull(guest, "guest");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(answer, "answer");
        if (PinPad.handles(action)) {
            return press(guest, action, answer);
        }
        // A screen is another way to type the command, so it asks the command's permission.
        return switch (action) {
            case AuthScreens.LOGIN ->
                guest.may(LOGIN_PERMISSION) ? login(guest, answer) : CompletableFuture.completedFuture(null);
            case AuthScreens.REGISTER ->
                guest.may(REGISTER_PERMISSION) ? register(guest, answer) : CompletableFuture.completedFuture(null);
            case AuthScreens.REPLACE ->
                guest.may(LOGIN_PERMISSION) ? replace(guest, answer) : CompletableFuture.completedFuture(null);
            case AuthScreens.CANCEL -> cancel(guest);
            case AuthScreens.CLAIM ->
                guest.may(CLAIM_PERMISSION) && screens.offersClaim()
                        ? claim(guest)
                        : CompletableFuture.completedFuture(null);
            default -> CompletableFuture.completedFuture(null);
        };
    }

    private CompletableFuture<Void> press(Guest guest, String action, Answer answer) {
        boolean registers = registering.test(guest.id());
        boolean twice = registers && passwords.get().confirmOnRegister();
        PinPad.Step step = pad.press(guest.id(), action, twice, passwords.get().maxLength());
        return switch (step) {
            case PinPad.Shown shown -> {
                shower.show(
                        guest.audience(),
                        screens.pad(guest.audience(), null, shown.entered(), shown.repeating(), registers));
                yield CompletableFuture.completedFuture(null);
            }
            case PinPad.Code code -> {
                Answer typed = new Typed(code, answer);
                yield registers ? answer(guest, AuthScreens.REGISTER, typed) : answer(guest, AuthScreens.LOGIN, typed);
            }
        };
    }

    /** The code from the pad, read as the fields a typed screen sends, with the rules box as pressed. */
    private record Typed(PinPad.Code code, Answer pressed) implements Answer {
        @Override
        public @Nullable String text(String key) {
            return switch (key) {
                case AuthScreens.PASSWORD -> code.first();
                case AuthScreens.CONFIRM -> code.second();
                default -> null;
            };
        }

        @Override
        public boolean ticked(String key) {
            return pressed.ticked(key);
        }
    }

    private CompletableFuture<Void> login(Guest guest, Answer answer) {
        choose(guest, answer);
        String typed = orEmpty(answer.text(AuthScreens.PASSWORD));
        return guest.after(attempt(guest, Password.of(typed)), result -> loggedIn(guest, result));
    }

    private void loggedIn(Guest guest, LoginResult result) {
        Audience reader = guest.audience();
        switch (result) {
            case LoginResult.LoggedIn loggedIn -> letIn(guest, AuthSettings.Moment.LOGGED_IN);
            case LoginResult.Wrong wrong -> {
                guest.fire(AuthSettings.Moment.WRONG_PASSWORD);
                shower.show(
                        reader,
                        screens.login(
                                reader,
                                messages.render(
                                        reader,
                                        Keys.LOGIN_WRONG,
                                        Text.placeholder("left", String.valueOf(wrong.attemptsLeft())))));
            }
            case LoginResult.TooManyAttempts tooMany -> {
                guest.fire(AuthSettings.Moment.TOO_MANY_ATTEMPTS);
                guest.kick(messages.render(reader, Keys.KICK_TOO_MANY_ATTEMPTS));
            }
            case LoginResult.Banned banned -> {
                guest.fire(AuthSettings.Moment.BANNED);
                guest.kick(messages.render(reader, Keys.KICK_BANNED, times.placeholder(reader, banned.lasts())));
            }
            case LoginResult.MustReplace mustReplace -> shower.show(reader, screens.replace(reader, mustReplace.why()));
            case LoginResult.NotRegistered notRegistered -> shower.show(reader, screens.register(reader, null));
            case LoginResult.AlreadyLoggedIn already -> shower.close(reader);
            case LoginResult.Gone gone -> {}
        }
    }

    private CompletableFuture<Void> register(Guest guest, Answer answer) {
        Audience reader = guest.audience();
        if (settings.get().rules().enabled() && !answer.ticked(AuthScreens.ACCEPT)) {
            again(reader, messages.render(reader, Keys.REGISTER_RULES_NEEDED));
            return CompletableFuture.completedFuture(null);
        }
        choose(guest, answer);
        String typed = orEmpty(answer.text(AuthScreens.PASSWORD));
        String repeated = passwords.get().confirmOnRegister() ? orEmpty(answer.text(AuthScreens.CONFIRM)) : typed;
        return guest.after(
                registration(guest, Password.of(typed), Password.of(repeated)), result -> registered(guest, result));
    }

    private void registered(Guest guest, RegisterResult result) {
        Audience reader = guest.audience();
        switch (result) {
            case RegisterResult.Created created -> letIn(guest, AuthSettings.Moment.REGISTERED);
            case RegisterResult.Mismatch mismatch -> again(reader, messages.render(reader, Keys.REGISTER_MISMATCH));
            case RegisterResult.Rejected rejected ->
                again(
                        reader,
                        PasswordWords.reason(messages, reader, rejected.verdict())
                                .orElse(null));
            case RegisterResult.NameTaken taken ->
                again(
                        reader,
                        messages.render(
                                reader,
                                Keys.REGISTER_NAME_TAKEN,
                                Text.placeholder("name", taken.stored().value())));
            case RegisterResult.TooManyAccounts tooMany ->
                again(reader, messages.render(reader, Keys.REGISTER_TOO_MANY_ACCOUNTS));
            case RegisterResult.AlreadyRegistered already -> shower.show(reader, screens.login(reader, null));
            case RegisterResult.Gone gone -> {}
        }
    }

    /** A new password, typed twice, in place of the weak one the login was held up by. */
    private CompletableFuture<Void> replace(Guest guest, Answer answer) {
        Audience reader = guest.audience();
        Password fresh = Password.of(orEmpty(answer.text(AuthScreens.PASSWORD)));
        try (Password repeated = Password.of(orEmpty(answer.text(AuthScreens.CONFIRM)))) {
            if (!fresh.sameAs(repeated)) {
                fresh.close();
                shower.show(reader, screens.replace(reader, messages.render(reader, Keys.REGISTER_MISMATCH)));
                return CompletableFuture.completedFuture(null);
            }
        }
        return guest.after(replacer.replace(guest.id(), fresh), result -> replaced(guest, result));
    }

    private void replaced(Guest guest, ChangeResult result) {
        Audience reader = guest.audience();
        switch (result) {
            case ChangeResult.Replaced replaced -> letIn(guest, AuthSettings.Moment.LOGGED_IN);
            case ChangeResult.Rejected rejected -> shower.show(reader, screens.replace(reader, rejected.verdict()));
            case ChangeResult.Changed changed -> shower.close(reader);
            case ChangeResult.NotLoggedIn notLoggedIn -> shower.show(reader, screens.login(reader, null));
            case ChangeResult.WrongOld wrongOld -> shower.show(reader, screens.login(reader, null));
            case ChangeResult.Gone gone -> {}
        }
    }

    private CompletableFuture<LoginResult> attempt(Guest guest, Password password) {
        return guest.opening().isPresent()
                ? logIn.run(guest.id(), guest.opening().getAsLong(), password, guest.address())
                : logIn.run(guest.id(), password, guest.address());
    }

    private CompletableFuture<RegisterResult> registration(Guest guest, Password typed, Password repeated) {
        PlayerName name = new PlayerName(guest.name());
        return guest.opening().isPresent()
                ? register.run(guest.id(), guest.opening().getAsLong(), name, typed, repeated, guest.address())
                : register.run(guest.id(), name, typed, repeated, guest.address());
    }

    private CompletableFuture<Void> cancel(Guest guest) {
        pad.forget(guest.id());
        if (settings.get().cancel() == ScreenSettings.Cancel.KICK) {
            guest.kick(messages.render(guest.audience(), Keys.KICK_CANCELLED));
        } else {
            shower.close(guest.audience());
            guest.release();
        }
        return CompletableFuture.completedFuture(null);
    }

    /** The next connection from here is asked, so the player is sent away to make it; anything else is a reason. */
    private CompletableFuture<Void> claim(Guest guest) {
        Audience reader = guest.audience();
        switch (claims.claim(guest.id(), guest.address())) {
            case ASKED -> {
                pad.forget(guest.id());
                guest.kick(messages.render(reader, Keys.KICK_CLAIMED));
            }
            case ALREADY_PREMIUM ->
                shower.show(reader, screens.login(reader, messages.render(reader, Keys.CLAIM_ALREADY_PREMIUM)));
            case NOT_THIS_RULE ->
                shower.show(reader, screens.login(reader, messages.render(reader, Keys.CLAIM_NOT_THIS_RULE)));
            case GONE -> {
                // The connection is leaving; there is nobody to answer.
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Every press says again what the box holds, so a tick on an earlier press does not outlive it. A tick counts
     * only while the screens ask it; a client cannot choose through a box it was not shown.
     */
    private void choose(Guest guest, Answer answer) {
        choices.chose(guest.id(), screens.asksToRemember() && answer.ticked(AuthScreens.REMEMBER));
    }

    private void again(Audience reader, @Nullable Component reason) {
        shower.show(reader, screens.register(reader, reason));
    }

    private void letIn(Guest guest, AuthSettings.Moment moment) {
        shower.close(guest.audience());
        guest.letIn(moment);
    }

    private static String orEmpty(@Nullable String text) {
        return text == null ? "" : text;
    }
}
