package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.PasswordWords;
import com.uxplima.auth.application.ChangePassword;
import com.uxplima.auth.application.ChangeResult;
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
import com.uxplima.uxmlib.text.message.Messages;

/**
 * {@code /changepassword <old> <new>}. While {@code passwords.old-on-change} is off, {@code /changepassword <new>}
 * is enough, and a player who writes both still has the old one checked.
 */
@FromConfig(value = ChangePasswordCommand.KEY, fallbackName = "changepassword", description = "Change your password")
public final class ChangePasswordCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "changepassword";

    private final ChangePassword change;
    private final Door door;
    private final MomentRunner moments;
    private final Supplier<AuthSettings.Passwords> passwords;
    private final Messages messages;
    private final Replies replies;

    public ChangePasswordCommand(
            ChangePassword change,
            Door door,
            MomentRunner moments,
            Supplier<AuthSettings.Passwords> passwords,
            Messages messages,
            Replies replies) {
        this.change = Objects.requireNonNull(change, "change");
        this.door = Objects.requireNonNull(door, "door");
        this.moments = Objects.requireNonNull(moments, "moments");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.replies = Objects.requireNonNull(replies, "replies");
    }

    @Subcommand("")
    @Permission("uxmauth.changepassword")
    @PlayerOnly
    public void changePassword(
            Sender sender, @Arg("first") String first, @Arg(value = "second", optional = true) String second) {
        CompletableFuture<Void> unused = attempt((Player) sender.bukkit(), first, second);
    }

    /** The whole command, answering when the player has been told. */
    CompletableFuture<Void> attempt(Player player, String first, String second) {
        CompletableFuture<ChangeResult> answer;
        if (!second.isEmpty()) {
            answer = change.run(player.getUniqueId(), Password.of(first), Password.of(second));
        } else if (passwords.get().oldOnChange()) {
            messages.send(player, Keys.CHANGE_NEEDS_BOTH);
            return CompletableFuture.completedFuture(null);
        } else {
            answer = change.replace(player.getUniqueId(), Password.of(first));
        }
        return replies.on(player, answer, result -> answer(player, result));
    }

    private void answer(Player player, ChangeResult result) {
        switch (result) {
            case ChangeResult.Changed changed -> moments.fire(player, AuthSettings.Moment.PASSWORD_CHANGED);
            case ChangeResult.Replaced replaced -> door.open(player, AuthSettings.Moment.LOGGED_IN);
            case ChangeResult.WrongOld wrongOld -> messages.send(player, Keys.CHANGE_WRONG_OLD);
            case ChangeResult.Rejected rejected ->
                PasswordWords.reason(messages, player, rejected.verdict()).ifPresent(player::sendMessage);
            case ChangeResult.NotLoggedIn notLoggedIn -> messages.send(player, Keys.CHANGE_NOT_LOGGED_IN);
            case ChangeResult.Gone gone -> {}
        }
    }
}
