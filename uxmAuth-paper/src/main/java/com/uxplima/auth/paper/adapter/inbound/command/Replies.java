package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.lane.LaneFullException;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Brings the answer of a use case back to the player's own thread.
 *
 * <p>A hash finishes on the lane, and nothing that touches a player may run there. A full lane is told to the
 * player as a moment of patience; anything else that failed is told as an error and written to the console.
 */
public final class Replies {

    private final Scheduler scheduler;
    private final Messages messages;
    private final Logger logger;

    public Replies(Scheduler scheduler, Messages messages, Logger logger) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Hands the answer to {@code then} on the player's thread, and answers when that has run. */
    public <T> CompletableFuture<Void> on(Player player, CompletableFuture<T> answer, Consumer<T> then) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        CompletableFuture<Void> unused = answer.handle((result, failure) -> {
            scheduler.entity(player, () -> {
                try {
                    if (failure == null) {
                        then.accept(result);
                    } else {
                        fail(player, failure);
                    }
                } finally {
                    done.complete(null);
                }
            });
            return null;
        });
        return done;
    }

    private void fail(Player player, Throwable failure) {
        Throwable cause = failure instanceof CompletionException wrapped && wrapped.getCause() != null
                ? wrapped.getCause()
                : failure;
        if (cause instanceof LaneFullException) {
            messages.send(player, Keys.BUSY);
            return;
        }
        logger.log(Level.WARNING, "A command of " + player.getName() + " failed", cause);
        messages.send(player, Keys.COMMAND_INTERNAL_ERROR);
    }
}
