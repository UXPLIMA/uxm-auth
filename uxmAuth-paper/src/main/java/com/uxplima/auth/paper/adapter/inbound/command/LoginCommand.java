package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.TimeWords;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/** {@code /login <password>}. */
@FromConfig(value = LoginCommand.KEY, fallbackName = "login", description = "Log in with your password")
public final class LoginCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "login";

    private final LogIn logIn;
    private final Door door;
    private final Function<Player, OptionalLong> openings;
    private final MomentRunner moments;
    private final Messages messages;
    private final Replies replies;
    private final TimeWords times;

    /** @param openings the opening of the session each player stands in the world under */
    public LoginCommand(
            LogIn logIn,
            Door door,
            Function<Player, OptionalLong> openings,
            MomentRunner moments,
            Messages messages,
            Replies replies) {
        this.logIn = Objects.requireNonNull(logIn, "logIn");
        this.door = Objects.requireNonNull(door, "door");
        this.openings = Objects.requireNonNull(openings, "openings");
        this.moments = Objects.requireNonNull(moments, "moments");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.times = new TimeWords(messages);
    }

    @Subcommand("")
    @Permission("uxmauth.login")
    @PlayerOnly
    public void login(Sender sender, @Arg("password") String password) {
        CompletableFuture<Void> unused = attempt((Player) sender.bukkit(), password);
    }

    /** The whole command, answering when the player has been told. */
    CompletableFuture<Void> attempt(Player player, String password) {
        OptionalLong opening = openings.apply(player);
        Password typed = Password.of(password);
        CompletableFuture<LoginResult> work = opening.isPresent()
                ? logIn.run(player.getUniqueId(), opening.getAsLong(), typed, Addresses.of(player))
                : logIn.run(player.getUniqueId(), typed, Addresses.of(player));
        return replies.on(player, work, result -> answer(player, result));
    }

    private void answer(Player player, LoginResult result) {
        switch (result) {
            case LoginResult.LoggedIn loggedIn -> door.open(player, AuthSettings.Moment.LOGGED_IN);
            case LoginResult.Wrong wrong -> {
                moments.fire(player, AuthSettings.Moment.WRONG_PASSWORD);
                messages.send(player, Keys.LOGIN_WRONG, Text.placeholder("left", String.valueOf(wrong.attemptsLeft())));
            }
            case LoginResult.TooManyAttempts tooMany -> {
                moments.fire(player, AuthSettings.Moment.TOO_MANY_ATTEMPTS);
                player.kick(messages.render(player, Keys.KICK_TOO_MANY_ATTEMPTS));
            }
            case LoginResult.Banned banned -> {
                moments.fire(player, AuthSettings.Moment.BANNED);
                player.kick(messages.render(player, Keys.KICK_BANNED, times.placeholder(player, banned.lasts())));
            }
            case LoginResult.NotRegistered notRegistered -> messages.send(player, Keys.LOGIN_NOT_REGISTERED);
            case LoginResult.AlreadyLoggedIn already -> messages.send(player, Keys.LOGIN_ALREADY);
            case LoginResult.Gone gone -> {}
        }
    }
}
