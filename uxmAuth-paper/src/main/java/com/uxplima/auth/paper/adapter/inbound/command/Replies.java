package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
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
        return on((CommandSender) player, answer, then);
    }

    /**
     * Hands the answer to {@code then} on the sender's thread, a player's own or the global one for the console,
     * and answers when that has run.
     */
    public <T> CompletableFuture<Void> on(CommandSender sender, CompletableFuture<T> answer, Consumer<T> then) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        CompletableFuture<Void> unused = answer.handle((result, failure) -> {
            Runnable task = () -> {
                try {
                    if (failure == null) {
                        then.accept(result);
                    } else {
                        fail(sender, failure);
                    }
                } finally {
                    done.complete(null);
                }
            };
            if (sender instanceof Player player) {
                scheduler.entity(player, task);
            } else {
                scheduler.global(task);
            }
            return null;
        });
        return done;
    }

    private void fail(CommandSender sender, Throwable failure) {
        Throwable cause = failure instanceof CompletionException wrapped && wrapped.getCause() != null
                ? wrapped.getCause()
                : failure;
        if (cause instanceof LaneFullException) {
            messages.send(sender, Keys.BUSY);
            return;
        }
        logger.log(Level.WARNING, "A command of " + sender.getName() + " failed", cause);
        messages.send(sender, Keys.COMMAND_INTERNAL_ERROR);
    }
}
