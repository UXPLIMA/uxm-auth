package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * What a server does when staff remove an account, or change how it logs in: its player here is sent away, on their
 * own thread, with the reason.
 */
public final class RemovedAccounts implements Consumer<Bulletin> {

    private final Function<UUID, @Nullable Player> online;
    private final Scheduler scheduler;
    private final Messages messages;

    /** @param online the player of this account on this server, when there is one */
    public RemovedAccounts(Function<UUID, @Nullable Player> online, Scheduler scheduler, Messages messages) {
        this.online = Objects.requireNonNull(online, "online");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void accept(Bulletin bulletin) {
        switch (Objects.requireNonNull(bulletin, "bulletin")) {
            case Bulletin.Unregistered removed -> sendAway(removed.account(), Keys.STAFF_REMOVED_KICK);
            case Bulletin.Rejoin rejoin -> sendAway(rejoin.account(), Keys.STAFF_REJOIN_KICK);
            case Bulletin.SessionsEnded ended -> {}
            case Bulletin.LetIn letIn -> {}
            case Bulletin.Alert alert -> {}
        }
    }

    private void sendAway(UUID account, Keys reason) {
        Player player = online.apply(account);
        if (player != null) {
            scheduler.entity(player, () -> player.kick(messages.render(player, reason)));
        }
    }
}
