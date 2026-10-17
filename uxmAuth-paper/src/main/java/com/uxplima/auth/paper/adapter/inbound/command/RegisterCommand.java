package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.RegisterAccount;
import com.uxplima.auth.application.RegisterResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * {@code /register <password> <confirm>}. The confirmation is asked for while {@code passwords.confirm-on-register}
 * is on; with it off, one password is enough.
 */
@FromConfig(value = RegisterCommand.KEY, fallbackName = "register", description = "Make an account with a password")
public final class RegisterCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "register";

    private final RegisterAccount register;
    private final Door door;
    private final Supplier<AuthSettings.Passwords> passwords;
    private final Messages messages;
    private final Replies replies;

    public RegisterCommand(
            RegisterAccount register,
            Door door,
            Supplier<AuthSettings.Passwords> passwords,
            Messages messages,
            Replies replies) {
        this.register = Objects.requireNonNull(register, "register");
        this.door = Objects.requireNonNull(door, "door");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.replies = Objects.requireNonNull(replies, "replies");
    }

    @Subcommand("")
    @Permission("uxmauth.register")
    @PlayerOnly
    public void register(
            Sender sender, @Arg("password") String password, @Arg(value = "confirm", optional = true) String confirm) {
        CompletableFuture<Void> unused = attempt((Player) sender.bukkit(), password, confirm);
    }

    /** The whole command, answering when the player has been told. */
    CompletableFuture<Void> attempt(Player player, String password, String confirm) {
        boolean asksTwice = passwords.get().confirmOnRegister();
        if (asksTwice && confirm.isEmpty()) {
            messages.send(player, Keys.REGISTER_CONFIRM_NEEDED);
            return CompletableFuture.completedFuture(null);
        }
        String second = asksTwice ? confirm : password;
        return replies.on(
                player,
                register.run(
                        player.getUniqueId(),
                        new PlayerName(player.getName()),
                        Password.of(password),
                        Password.of(second),
                        Addresses.of(player)),
                result -> answer(player, result));
    }

    private void answer(Player player, RegisterResult result) {
        switch (result) {
            case RegisterResult.Created created -> door.open(player, AuthSettings.Moment.REGISTERED);
            case RegisterResult.Mismatch mismatch -> messages.send(player, Keys.REGISTER_MISMATCH);
            case RegisterResult.Rejected rejected -> PasswordWords.say(messages, player, rejected.verdict());
            case RegisterResult.NameTaken taken ->
                messages.send(
                        player,
                        Keys.REGISTER_NAME_TAKEN,
                        Text.placeholder("name", taken.stored().value()));
            case RegisterResult.AlreadyRegistered already -> messages.send(player, Keys.REGISTER_ALREADY);
            case RegisterResult.Gone gone -> {}
        }
    }
}
