package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.Objects;
import java.util.UUID;
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
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * What a server does when staff let an account in: its player, waiting here, is let in as forced. The session is
 * written on the lane, and the door opens on the player's own thread.
 */
public final class ForcedLogins implements Consumer<Bulletin> {

    private final Function<UUID, @Nullable Player> online;
    private final ForceLogin force;
    private final HashingLane lane;
    private final Replies replies;
    private final Door door;
    private final Messages messages;

    /** @param online the player of this account on this server, when there is one */
    public ForcedLogins(
            Function<UUID, @Nullable Player> online,
            ForceLogin force,
            HashingLane lane,
            Replies replies,
            Door door,
            Messages messages) {
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
            }
        }
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
