package com.uxplima.auth.paper.adapter.inbound.screen;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;

/** A guest who stands in the world: the door opens for them, and the answer comes back on their thread. */
public final class PlayerGuest implements Guest {

    private final Player player;
    private final OptionalLong opening;
    private final Door door;
    private final MomentRunner moments;
    private final Replies replies;

    /** @param opening the opening of the session the player stands in the world under, empty if unknown */
    public PlayerGuest(Player player, OptionalLong opening, Door door, MomentRunner moments, Replies replies) {
        this.player = Objects.requireNonNull(player, "player");
        this.opening = Objects.requireNonNull(opening, "opening");
        this.door = Objects.requireNonNull(door, "door");
        this.moments = Objects.requireNonNull(moments, "moments");
        this.replies = Objects.requireNonNull(replies, "replies");
    }

    @Override
    public UUID id() {
        return player.getUniqueId();
    }

    @Override
    public OptionalLong opening() {
        return opening;
    }

    @Override
    public String name() {
        return player.getName();
    }

    @Override
    public String address() {
        InetSocketAddress address = player.getAddress();
        return address == null || address.getAddress() == null
                ? "unknown"
                : address.getAddress().getHostAddress();
    }

    @Override
    public Audience audience() {
        return player;
    }

    @Override
    public boolean may(String permission) {
        return player.hasPermission(Objects.requireNonNull(permission, "permission"));
    }

    @Override
    public void fire(AuthSettings.Moment moment) {
        moments.fire(player, moment);
    }

    @Override
    public void kick(Component reason) {
        player.kick(reason);
    }

    @Override
    public void letIn(AuthSettings.Moment moment) {
        door.open(player, moment);
    }

    @Override
    public void release() {
        // The player already stands in the frozen world; closing the screen is all there is to do.
    }

    @Override
    public <T> CompletableFuture<Void> after(CompletableFuture<T> work, Consumer<T> then) {
        return replies.on(player, work, then);
    }
}
