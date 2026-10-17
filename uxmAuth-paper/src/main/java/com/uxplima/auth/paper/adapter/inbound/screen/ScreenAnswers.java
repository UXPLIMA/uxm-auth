package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.RegisterAccount;
import com.uxplima.auth.application.RegisterResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.paper.adapter.inbound.command.PasswordWords;
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

    private final AuthScreens screens;
    private final RegisterAccount register;
    private final LogIn logIn;
    private final Supplier<ScreenSettings> settings;
    private final Supplier<AuthSettings.Passwords> passwords;
    private final Messages messages;
    private final ScreenShower shower;

    public ScreenAnswers(
            AuthScreens screens,
            RegisterAccount register,
            LogIn logIn,
            Supplier<ScreenSettings> settings,
            Supplier<AuthSettings.Passwords> passwords,
            Messages messages,
            ScreenShower shower) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.register = Objects.requireNonNull(register, "register");
        this.logIn = Objects.requireNonNull(logIn, "logIn");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.shower = Objects.requireNonNull(shower, "shower");
    }

    /** Runs one press, answering when the guest has been shown the outcome. */
    public CompletableFuture<Void> answer(Guest guest, String action, Answer answer) {
        Objects.requireNonNull(guest, "guest");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(answer, "answer");
        // A screen is another way to type the command, so it asks the command's permission.
        return switch (action) {
            case AuthScreens.LOGIN ->
                guest.may(LOGIN_PERMISSION) ? login(guest, answer) : CompletableFuture.completedFuture(null);
            case AuthScreens.REGISTER ->
                guest.may(REGISTER_PERMISSION) ? register(guest, answer) : CompletableFuture.completedFuture(null);
            case AuthScreens.CANCEL -> cancel(guest);
            default -> CompletableFuture.completedFuture(null);
        };
    }

    private CompletableFuture<Void> login(Guest guest, Answer answer) {
        String typed = orEmpty(answer.text(AuthScreens.PASSWORD));
        Audience reader = guest.audience();
        Password password = Password.of(typed);
        CompletableFuture<LoginResult> work = guest.opening().isPresent()
                ? logIn.run(guest.id(), guest.opening().getAsLong(), password, guest.address())
                : logIn.run(guest.id(), password, guest.address());
        return guest.after(work, result -> {
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
                case LoginResult.NotRegistered notRegistered -> shower.show(reader, screens.register(reader, null));
                case LoginResult.AlreadyLoggedIn already -> shower.close(reader);
                case LoginResult.Gone gone -> {}
            }
        });
    }

    private CompletableFuture<Void> register(Guest guest, Answer answer) {
        Audience reader = guest.audience();
        if (settings.get().rules().enabled() && !answer.ticked(AuthScreens.ACCEPT)) {
            again(reader, messages.render(reader, Keys.REGISTER_RULES_NEEDED));
            return CompletableFuture.completedFuture(null);
        }
        String typed = orEmpty(answer.text(AuthScreens.PASSWORD));
        String repeated = passwords.get().confirmOnRegister() ? orEmpty(answer.text(AuthScreens.CONFIRM)) : typed;
        return guest.after(registration(guest, Password.of(typed), Password.of(repeated)), result -> {
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
                case RegisterResult.AlreadyRegistered already -> shower.show(reader, screens.login(reader, null));
                case RegisterResult.Gone gone -> {}
            }
        });
    }

    private CompletableFuture<RegisterResult> registration(Guest guest, Password typed, Password repeated) {
        PlayerName name = new PlayerName(guest.name());
        return guest.opening().isPresent()
                ? register.run(guest.id(), guest.opening().getAsLong(), name, typed, repeated, guest.address())
                : register.run(guest.id(), name, typed, repeated, guest.address());
    }

    private CompletableFuture<Void> cancel(Guest guest) {
        if (settings.get().cancel() == ScreenSettings.Cancel.KICK) {
            guest.kick(messages.render(guest.audience(), Keys.KICK_CANCELLED));
        } else {
            shower.close(guest.audience());
            guest.release();
        }
        return CompletableFuture.completedFuture(null);
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
