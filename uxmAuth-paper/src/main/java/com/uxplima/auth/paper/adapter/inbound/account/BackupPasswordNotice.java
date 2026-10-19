package com.uxplima.auth.paper.adapter.inbound.account;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import com.uxplima.auth.application.account.BackupPassword;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.paper.adapter.inbound.hold.LoginRecords;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * At a premium login, an account with no password gets its backup one, and its player reads it once: in chat to
 * click and copy, and in a form on Bedrock. It goes to the player logged in as the account only, never to another
 * connection under the name, the console or the log. It is made and hashed on the hashing lane.
 */
public final class BackupPasswordNotice implements LoginRecords.Landed {

    private final BackupPassword backup;
    private final HashingLane lane;
    private final LoginNotices.Reach reach;
    private final Messages messages;
    private final BiConsumer<Player, String> form;
    private final Predicate<UUID> bedrock;

    /** @param form shows a Bedrock player the password, to copy */
    public BackupPasswordNotice(
            BackupPassword backup,
            HashingLane lane,
            LoginNotices.Reach reach,
            Messages messages,
            BiConsumer<Player, String> form,
            Predicate<UUID> bedrock) {
        this.backup = Objects.requireNonNull(backup, "backup");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.reach = Objects.requireNonNull(reach, "reach");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.form = Objects.requireNonNull(form, "form");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
    }

    @Override
    public void landed(UUID account, PlayerName name, String address, AuthMethod method) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(name, "name");
        if (Objects.requireNonNull(method, "method") != AuthMethod.PREMIUM) {
            return;
        }
        CompletableFuture<Void> unused = lane.submit(() -> backup.issue(account))
                .thenAccept(issued -> issued.ifPresent(password -> reach.tell(name, account, player -> {
                    Component copied = Component.text(password).clickEvent(ClickEvent.copyToClipboard(password));
                    messages.send(player, Keys.ACCOUNT_BACKUP_PASSWORD, Text.component("password", copied));
                    if (bedrock.test(player.getUniqueId())) {
                        form.accept(player, password);
                    }
                })));
    }
}
