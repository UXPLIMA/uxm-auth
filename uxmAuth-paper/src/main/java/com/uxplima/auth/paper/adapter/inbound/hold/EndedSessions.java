package com.uxplima.auth.paper.adapter.inbound.hold;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.LogOut;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * What a server does when an account's sessions end: a player of it logged in here reads why and stands at the
 * door again, unless they made the change themselves. A bulletin arrives on whatever thread brought it, so the
 * player is reached on their own.
 */
public final class EndedSessions implements Consumer<Bulletin> {

    private final Function<UUID, @Nullable Player> online;
    private final Scheduler scheduler;
    private final LogOut logOut;
    private final Door door;
    private final MomentRunner moments;
    private final Messages messages;

    /** @param online the player of this account on this server, when there is one */
    public EndedSessions(
            Function<UUID, @Nullable Player> online,
            Scheduler scheduler,
            LogOut logOut,
            Door door,
            MomentRunner moments,
            Messages messages) {
        this.online = Objects.requireNonNull(online, "online");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logOut = Objects.requireNonNull(logOut, "logOut");
        this.door = Objects.requireNonNull(door, "door");
        this.moments = Objects.requireNonNull(moments, "moments");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void accept(Bulletin bulletin) {
        switch (Objects.requireNonNull(bulletin, "bulletin")) {
            case Bulletin.SessionsEnded ended -> {
                Player player = ended.keepOnline() ? null : online.apply(ended.account());
                if (player != null) {
                    scheduler.entity(player, () -> end(player));
                }
            }
        }
    }

    private void end(Player player) {
        switch (logOut.run(player.getUniqueId())) {
            case NOT_LOGGED_IN -> {}
            // No password to come back with: the way back in is a new join.
            case NO_PASSWORD -> player.kick(messages.render(player, Keys.LOGOUT_SESSIONS_ENDED));
            case LOGGED_OUT -> {
                messages.send(player, Keys.LOGOUT_SESSIONS_ENDED);
                moments.fire(player, AuthSettings.Moment.LOGGED_OUT);
                door.hold(player);
            }
        }
    }
}
