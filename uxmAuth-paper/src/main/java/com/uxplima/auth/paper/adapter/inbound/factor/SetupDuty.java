package com.uxplima.auth.paper.adapter.inbound.factor;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * A player holding the node the operator names plays only with an authenticator app on. The node is read at every
 * login, so a player who gets it is asked from the next one and a player who loses it is not asked again. The
 * player is held at once and the table read off their thread: one whose app is on is let in, anyone else is told to
 * set it up, and a Bedrock player gets the setup form. The login code by mail answers the duty as the app does.
 */
public final class SetupDuty implements Door.Duty {

    private final OwedSetups owed;
    private final Supplier<String> node;
    private final Predicate<UUID> hasFactor;
    private final FactorSetup setup;
    private final Predicate<UUID> bedrock;
    private final HashingLane lane;
    private final Replies replies;
    private final Messages messages;

    /**
     * @param node the permission that requires the app; blank requires it of nobody
     * @param hasFactor whether the account has a second factor already, the app or the code by mail; reads the table
     */
    public SetupDuty(
            OwedSetups owed,
            Supplier<String> node,
            Predicate<UUID> hasFactor,
            FactorSetup setup,
            Predicate<UUID> bedrock,
            HashingLane lane,
            Replies replies,
            Messages messages) {
        this.owed = Objects.requireNonNull(owed, "owed");
        this.node = Objects.requireNonNull(node, "node");
        this.hasFactor = Objects.requireNonNull(hasFactor, "hasFactor");
        this.setup = Objects.requireNonNull(setup, "setup");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Only a node granted outright counts: the operator names it, so the server knows no default for it and would
     * otherwise hand it to every operator.
     */
    @Override
    public boolean owes(Player player) {
        Objects.requireNonNull(player, "player");
        String required = node.get();
        return !required.isBlank() && player.isPermissionSet(required) && player.hasPermission(required);
    }

    @Override
    public void hold(Player player, AuthState.Authenticated in, Runnable proceed) {
        owed.hold(player, in, proceed);
        UUID account = in.account().id();
        CompletableFuture<Void> unused =
                replies.on(player, lane.submit(() -> hasFactor.test(account)), active -> checked(player, active));
    }

    private void checked(Player player, boolean active) {
        if (active) {
            owed.release(player);
            return;
        }
        messages.send(player, Keys.FACTOR_REQUIRED);
        if (bedrock.test(player.getUniqueId())) {
            CompletableFuture<Void> unused = setup.setup(player);
        }
    }
}
