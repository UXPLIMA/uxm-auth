package com.uxplima.auth.paper.adapter.inbound.account;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.PasswordWords;
import com.uxplima.auth.application.account.SwitchToCracked;
import com.uxplima.auth.application.account.SwitchToCracked.Switched;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * {@code /cracked <password>}: a premium player turns their own account cracked, and logs in with a password from
 * their next join. The password typed is the account's own, or its first when it has none. Alone, it tells a Java
 * player how to type it and gives a Bedrock player a form for it.
 */
@FromConfig(
        value = CrackedCommand.KEY,
        fallbackName = "cracked",
        description = "Turn your premium account cracked and log in with a password from now on")
public final class CrackedCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "cracked";

    private final SwitchToCracked toCracked;
    private final ProofForm form;
    private final Predicate<UUID> bedrock;
    private final Replies replies;
    private final Messages messages;

    public CrackedCommand(
            SwitchToCracked toCracked, ProofForm form, Predicate<UUID> bedrock, Replies replies, Messages messages) {
        this.toCracked = Objects.requireNonNull(toCracked, "toCracked");
        this.form = Objects.requireNonNull(form, "form");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Subcommand("")
    @Permission("uxmauth.cracked")
    @PlayerOnly
    public void cracked(Sender sender, @Arg(value = "password", optional = true) String password) {
        Player player = (Player) sender.bukkit();
        if (password.isEmpty()) {
            ask(player);
        } else {
            CompletableFuture<Void> unused = confirm(player, password);
        }
    }

    /** Tells the player how to switch, or shows a Bedrock player the form for it. */
    void ask(Player player) {
        if (bedrock.test(player.getUniqueId())) {
            form.show(player, Keys.ACCOUNT_CRACKED_FORM_TITLE, Keys.ACCOUNT_CRACKED_FORM_PASSWORD, typed -> {
                CompletableFuture<Void> unused = confirm(player, typed);
            });
            return;
        }
        messages.send(player, Keys.ACCOUNT_CRACKED_USAGE);
    }

    /** Switches the account when the password is its own or a first one the rules take. */
    CompletableFuture<Void> confirm(Player player, String password) {
        return replies.on(
                player, toCracked.confirm(player.getUniqueId(), password), switched -> answer(player, switched));
    }

    private void answer(Player player, Switched switched) {
        switch (switched) {
            // The bulletin sends the player away to join again as cracked, in their own words.
            case Switched.Done done -> {}
            case Switched.WrongPassword wrong -> messages.send(player, Keys.ACCOUNT_CRACKED_WRONG);
            case Switched.Refused refused ->
                PasswordWords.reason(messages, player, refused.verdict()).ifPresent(player::sendMessage);
            case Switched.NotPremium notPremium -> messages.send(player, Keys.ACCOUNT_CRACKED_NOT_PREMIUM);
            case Switched.NotLoggedIn notLoggedIn -> messages.send(player, Keys.ACCOUNT_LOG_IN_FIRST);
        }
    }
}
