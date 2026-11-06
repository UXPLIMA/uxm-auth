package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.staff.ForceLogin;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.paper.adapter.inbound.command.Addresses;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.hold.DialogWaits;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * What a server does when staff let an account in: its player, waiting here, is let in as forced. The session is
 * written on the lane, and the door opens on the player's own thread. A connection still at the dialog before the
 * world has no player yet; its wait ends as a login, and the world greets it.
 */
public final class ForcedLogins implements Consumer<Bulletin> {

    private final Function<UUID, @Nullable Player> online;
    private final DialogWaits dialogs;
    private final ForceLogin force;
    private final HashingLane lane;
    private final Replies replies;
    private final Door door;
    private final Messages messages;

    /**
     * @param online the player of this account on this server, when there is one
     * @param dialogs the connections waiting at the dialog, which have no player
     */
    public ForcedLogins(
            Function<UUID, @Nullable Player> online,
            DialogWaits dialogs,
            ForceLogin force,
            HashingLane lane,
            Replies replies,
            Door door,
            Messages messages) {
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.online = Objects.requireNonNull(online, "online");
        this.force = Objects.requireNonNull(force, "force");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.door = Objects.requireNonNull(door, "door");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void accept(Bulletin bulletin) {
        if (Objects.requireNonNull(bulletin, "bulletin") instanceof Bulletin.LetIn letIn) {
            Player player = online.apply(letIn.account());
            if (player != null) {
                letIn(player);
            } else {
                dialogs.address(letIn.account()).ifPresent(address -> letInAtTheDialog(letIn.account(), address));
            }
        }
    }

    /** No entity owns a connecting client, so its answer is taken on whichever thread finished the work. */
    private void letInAtTheDialog(UUID id, String address) {
        CompletableFuture<Void> unused = lane.submit(() -> force.admit(id, address))
                .thenAccept(in -> in.ifPresent(done -> dialogs.letIn(id, AuthSettings.Moment.LOGGED_IN)));
    }

    private void letIn(Player player) {
        String address = Addresses.of(player);
        UUID id = player.getUniqueId();
        var unused = replies.on(
                player,
                lane.submit(() -> force.admit(id, address)),
                in -> in.ifPresent(done -> {
                    messages.send(player, Keys.STAFF_LET_IN);
                    door.open(player, AuthSettings.Moment.LOGGED_IN);
                }));
    }
}
