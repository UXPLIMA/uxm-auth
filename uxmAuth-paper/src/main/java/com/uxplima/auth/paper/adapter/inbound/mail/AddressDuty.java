package com.uxplima.auth.paper.adapter.inbound.mail;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.factor.OwedSetups;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Where the operator requires an address, a password account plays only with a confirmed one. A new player meets it
 * right after registering, and an account without one meets it at its next login. The player is held at once and the
 * table read off their thread: one with an address is let in, anyone else is told to link one and shown how. The
 * address confirmed by {@code /email confirm} lets them in. An account without a password has nothing to recover.
 */
public final class AddressDuty implements Door.Duty, Listener {

    private final OwedSetups owed;
    private final BooleanSupplier required;
    private final Predicate<UUID> hasAddress;
    private final Consumer<Player> ask;
    private final HashingLane lane;
    private final Replies replies;
    private final Messages messages;

    /**
     * @param required whether an address is required now: the setting, and mail that can confirm one
     * @param hasAddress whether the account has a confirmed address; reads the table
     * @param ask shows a held player how to link an address, as a form to a Bedrock player
     */
    public AddressDuty(
            OwedSetups owed,
            BooleanSupplier required,
            Predicate<UUID> hasAddress,
            Consumer<Player> ask,
            HashingLane lane,
            Replies replies,
            Messages messages) {
        this.owed = Objects.requireNonNull(owed, "owed");
        this.required = Objects.requireNonNull(required, "required");
        this.hasAddress = Objects.requireNonNull(hasAddress, "hasAddress");
        this.ask = Objects.requireNonNull(ask, "ask");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public boolean owes(Player player) {
        Objects.requireNonNull(player, "player");
        return required.getAsBoolean();
    }

    @Override
    public void hold(Player player, AuthState.Authenticated in, Runnable proceed) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(proceed, "proceed");
        if (Objects.requireNonNull(in, "in").account().kind() != AccountKind.CRACKED) {
            proceed.run();
            return;
        }
        owed.hold(player, in, proceed);
        UUID account = in.account().id();
        CompletableFuture<Void> unused =
                replies.on(player, lane.submit(() -> hasAddress.test(account)), linked -> checked(player, linked));
    }

    private void checked(Player player, boolean linked) {
        if (linked) {
            owed.release(player);
            return;
        }
        messages.send(player, Keys.EMAIL_REQUIRED);
        ask.accept(player);
    }

    /** The player left: the hold ends with them, and the next login asks again. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        owed.forget(event.getPlayer().getUniqueId());
    }
}
