package com.uxplima.auth.paper.adapter.inbound.account;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
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
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * At a premium login, an account with no password gets its backup one, and its player reads it once: in chat to
 * click and copy, and in a form on Bedrock. It goes to the player only, never to the console or the log. It is
 * made and hashed on the hashing lane.
 */
public final class BackupPasswordNotice implements LoginRecords.Landed {

    private final BackupPassword backup;
    private final HashingLane lane;
    private final Function<PlayerName, @Nullable Player> online;
    private final Scheduler scheduler;
    private final Messages messages;
    private final BiConsumer<Player, String> form;
    private final Predicate<UUID> bedrock;

    /**
     * @param online the player under this name on this server, when there is one
     * @param form shows a Bedrock player the password, to copy
     */
    public BackupPasswordNotice(
            BackupPassword backup,
            HashingLane lane,
            Function<PlayerName, @Nullable Player> online,
            Scheduler scheduler,
            Messages messages,
            BiConsumer<Player, String> form,
            Predicate<UUID> bedrock) {
        this.backup = Objects.requireNonNull(backup, "backup");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.online = Objects.requireNonNull(online, "online");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
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
                .thenAccept(issued -> issued.ifPresent(password -> show(name, password)));
    }

    private void show(PlayerName name, String password) {
        Optional.ofNullable(online.apply(name))
                .ifPresent(player -> scheduler.entity(player, () -> {
                    Component copied = Component.text(password).clickEvent(ClickEvent.copyToClipboard(password));
                    messages.send(player, Keys.ACCOUNT_BACKUP_PASSWORD, Text.component("password", copied));
                    if (bedrock.test(player.getUniqueId())) {
                        form.accept(player, password);
                    }
                }));
    }
}
