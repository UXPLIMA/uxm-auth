package com.uxplima.auth.paper.adapter.inbound.account;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.account.DeleteOwnAccount;
import com.uxplima.auth.application.account.DeleteOwnAccount.Deleted;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * {@code /unregister}: alone, it asks, and the time to confirm starts. With the password, or a code from the app
 * when the account uses one, it deletes the account, and the player is sent away. A Bedrock player confirms in a
 * form. The proof is checked on the hashing lane; the player is answered on their own thread.
 */
@FromConfig(
        value = UnregisterCommand.KEY,
        fallbackName = "unregister",
        description = "Delete your own account, confirmed by your password or 2FA code")
public final class UnregisterCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "unregister";

    private final DeleteOwnAccount delete;
    private final ProofForm form;
    private final Predicate<UUID> bedrock;
    private final Replies replies;
    private final Messages messages;

    public UnregisterCommand(
            DeleteOwnAccount delete, ProofForm form, Predicate<UUID> bedrock, Replies replies, Messages messages) {
        this.delete = Objects.requireNonNull(delete, "delete");
        this.form = Objects.requireNonNull(form, "form");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Subcommand("")
    @Permission("uxmauth.unregister")
    @PlayerOnly
    public void unregister(Sender sender, @Arg(value = "proof", optional = true) String proof) {
        Player player = (Player) sender.bukkit();
        if (proof.isEmpty()) {
            ask(player);
        } else {
            CompletableFuture<Void> unused = confirm(player, proof);
        }
    }

    /** Opens the time to confirm, and on Bedrock shows the form to confirm in. */
    void ask(Player player) {
        Deleted asked = delete.ask(player.getUniqueId());
        if (asked instanceof Deleted.Asked open && bedrock.test(player.getUniqueId())) {
            Keys field = open.byCode() ? Keys.ACCOUNT_DELETE_FORM_CODE : Keys.ACCOUNT_DELETE_FORM_PASSWORD;
            form.show(player, Keys.ACCOUNT_DELETE_FORM_TITLE, field, typed -> {
                CompletableFuture<Void> unused = confirm(player, typed);
            });
            return;
        }
        answer(player, asked);
    }

    /** Deletes the account when the time is open and the proof is right; answers when the player has been told. */
    CompletableFuture<Void> confirm(Player player, String proof) {
        return replies.on(player, delete.confirm(player.getUniqueId(), proof), deleted -> answer(player, deleted));
    }

    private void answer(Player player, Deleted deleted) {
        switch (deleted) {
            case Deleted.Asked asked ->
                messages.send(
                        player,
                        asked.byCode() ? Keys.ACCOUNT_DELETE_ASK_CODE : Keys.ACCOUNT_DELETE_ASK_PASSWORD,
                        Text.placeholder(
                                "seconds", String.valueOf(asked.window().toSeconds())));
            // The bulletin sends the player away, in their own words, here and on every member of a network.
            case Deleted.Removed removed -> {}
            case Deleted.WrongProof wrong -> messages.send(player, Keys.ACCOUNT_DELETE_WRONG);
            case Deleted.NotAsked notAsked -> messages.send(player, Keys.ACCOUNT_DELETE_NOT_ASKED);
            case Deleted.NotLoggedIn notLoggedIn -> messages.send(player, Keys.ACCOUNT_LOG_IN_FIRST);
            case Deleted.NoProof noProof -> messages.send(player, Keys.ACCOUNT_DELETE_NO_PROOF);
        }
    }
}
