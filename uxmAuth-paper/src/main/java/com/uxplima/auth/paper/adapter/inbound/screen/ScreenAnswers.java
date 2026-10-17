package com.uxplima.auth.paper.adapter.inbound.screen;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

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
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * What a press on a screen does, for a player in the world: the same use cases as the commands, with a refusal
 * shown on the same screen and the reason on top.
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
    private final Door door;
    private final MomentRunner moments;
    private final Supplier<ScreenSettings> settings;
    private final Supplier<AuthSettings.Passwords> passwords;
    private final Messages messages;
    private final Replies replies;
    private final ScreenShower shower;

    public ScreenAnswers(
            AuthScreens screens,
            RegisterAccount register,
            LogIn logIn,
            Door door,
            MomentRunner moments,
            Supplier<ScreenSettings> settings,
            Supplier<AuthSettings.Passwords> passwords,
            Messages messages,
            Replies replies,
            ScreenShower shower) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.register = Objects.requireNonNull(register, "register");
        this.logIn = Objects.requireNonNull(logIn, "logIn");
        this.door = Objects.requireNonNull(door, "door");
        this.moments = Objects.requireNonNull(moments, "moments");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.shower = Objects.requireNonNull(shower, "shower");
    }

    /** Runs one press, answering when the player has been shown the outcome. */
    public CompletableFuture<Void> answer(Player player, String action, Answer answer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(answer, "answer");
        // A screen is another way to type the command, so it asks the command's permission.
        return switch (action) {
            case AuthScreens.LOGIN ->
                player.hasPermission(LOGIN_PERMISSION)
                        ? login(player, answer)
                        : CompletableFuture.completedFuture(null);
            case AuthScreens.REGISTER ->
                player.hasPermission(REGISTER_PERMISSION)
                        ? register(player, answer)
                        : CompletableFuture.completedFuture(null);
            case AuthScreens.CANCEL -> cancel(player);
            default -> CompletableFuture.completedFuture(null);
        };
    }

    private CompletableFuture<Void> login(Player player, Answer answer) {
        String typed = orEmpty(answer.text(AuthScreens.PASSWORD));
        return replies.on(player, logIn.run(player.getUniqueId(), Password.of(typed), address(player)), result -> {
            switch (result) {
                case LoginResult.LoggedIn loggedIn -> letIn(player, AuthSettings.Moment.LOGGED_IN);
                case LoginResult.Wrong wrong -> {
                    moments.fire(player, AuthSettings.Moment.WRONG_PASSWORD);
                    shower.show(
                            player,
                            screens.login(
                                    player,
                                    messages.render(
                                            player,
                                            Keys.LOGIN_WRONG,
                                            Text.placeholder("left", String.valueOf(wrong.attemptsLeft())))));
                }
                case LoginResult.TooManyAttempts tooMany -> {
                    moments.fire(player, AuthSettings.Moment.TOO_MANY_ATTEMPTS);
                    player.kick(messages.render(player, Keys.KICK_TOO_MANY_ATTEMPTS));
                }
                case LoginResult.NotRegistered notRegistered -> shower.show(player, screens.register(player, null));
                case LoginResult.AlreadyLoggedIn already -> shower.close(player);
                case LoginResult.Gone gone -> {}
            }
        });
    }

    private CompletableFuture<Void> register(Player player, Answer answer) {
        if (settings.get().rules().enabled() && !answer.ticked(AuthScreens.ACCEPT)) {
            shower.show(player, screens.register(player, messages.render(player, Keys.REGISTER_RULES_NEEDED)));
            return CompletableFuture.completedFuture(null);
        }
        String typed = orEmpty(answer.text(AuthScreens.PASSWORD));
        String again = passwords.get().confirmOnRegister() ? orEmpty(answer.text(AuthScreens.CONFIRM)) : typed;
        return replies.on(
                player,
                register.run(
                        player.getUniqueId(),
                        new PlayerName(player.getName()),
                        Password.of(typed),
                        Password.of(again),
                        address(player)),
                result -> {
                    switch (result) {
                        case RegisterResult.Created created -> letIn(player, AuthSettings.Moment.REGISTERED);
                        case RegisterResult.Mismatch mismatch ->
                            again(player, messages.render(player, Keys.REGISTER_MISMATCH));
                        case RegisterResult.Rejected rejected ->
                            again(
                                    player,
                                    PasswordWords.reason(messages, player, rejected.verdict())
                                            .orElse(null));
                        case RegisterResult.NameTaken taken ->
                            again(
                                    player,
                                    messages.render(
                                            player,
                                            Keys.REGISTER_NAME_TAKEN,
                                            Text.placeholder(
                                                    "name", taken.stored().value())));
                        case RegisterResult.AlreadyRegistered already ->
                            shower.show(player, screens.login(player, null));
                        case RegisterResult.Gone gone -> {}
                    }
                });
    }

    private CompletableFuture<Void> cancel(Player player) {
        if (settings.get().cancel() == ScreenSettings.Cancel.KICK) {
            player.kick(messages.render(player, Keys.KICK_CANCELLED));
        } else {
            shower.close(player);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void again(Player player, @Nullable Component reason) {
        shower.show(player, screens.register(player, reason));
    }

    private void letIn(Player player, AuthSettings.Moment moment) {
        shower.close(player);
        door.open(player, moment);
    }

    private static String orEmpty(@Nullable String text) {
        return text == null ? "" : text;
    }

    private static String address(Player player) {
        InetSocketAddress address = player.getAddress();
        return address == null || address.getAddress() == null
                ? "unknown"
                : address.getAddress().getHostAddress();
    }
}
