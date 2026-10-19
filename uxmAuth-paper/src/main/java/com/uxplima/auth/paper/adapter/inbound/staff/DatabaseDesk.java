package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.migrate.MoveDatabase;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * What the console does to the database from {@code /uxmauth database}. Only the console: the work changes where
 * every account lives, so no setting opens it in game. One runs at a time, off the server's threads.
 */
public final class DatabaseDesk implements DatabaseActions {

    private final MoveDatabase moves;
    private final Executor work;
    private final Replies replies;
    private final Messages messages;
    private final Consumer<String> log;
    private final AtomicBoolean busy = new AtomicBoolean();

    /**
     * @param work runs the copy, off every server thread
     * @param log where the line a move leaves goes, which is the console
     */
    public DatabaseDesk(MoveDatabase moves, Executor work, Replies replies, Messages messages, Consumer<String> log) {
        this.moves = Objects.requireNonNull(moves, "moves");
        this.work = Objects.requireNonNull(work, "work");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public CompletableFuture<Void> move(CommandSender sender) {
        if (!console(Objects.requireNonNull(sender, "sender"), "database move")) {
            return CompletableFuture.completedFuture(null);
        }
        if (!busy.compareAndSet(false, true)) {
            messages.send(sender, Keys.DATABASE_MOVE_RUNNING);
            return CompletableFuture.completedFuture(null);
        }
        messages.send(sender, Keys.DATABASE_MOVE_STARTED);
        CompletableFuture<MoveDatabase.Moved> moved;
        try {
            moved = CompletableFuture.supplyAsync(moves::move, work);
        } catch (RuntimeException refused) {
            // A refused hand-off must not leave every later move refused as running.
            busy.set(false);
            throw refused;
        }
        CompletableFuture<MoveDatabase.Moved> freed = moved.whenComplete((result, failure) -> busy.set(false));
        return replies.on(sender, freed, result -> tell(sender, result));
    }

    /** Whether the sender is the console, and tells anybody else it is not theirs to run. */
    private boolean console(CommandSender sender, String command) {
        if (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender) {
            return true;
        }
        messages.send(sender, Keys.DATABASE_CONSOLE_ONLY, Text.placeholder("command", command));
        return false;
    }

    private void tell(CommandSender sender, MoveDatabase.Moved moved) {
        switch (moved) {
            case MoveDatabase.Moved.Done done -> {
                long rows =
                        done.rows().values().stream().mapToLong(Long::longValue).sum();
                messages.send(
                        sender,
                        Keys.DATABASE_MOVE_DONE,
                        Text.placeholder("rows", String.valueOf(rows)),
                        Text.placeholder("tables", String.valueOf(done.rows().size())));
                log.accept(sender.getName() + " copied " + rows + " rows in "
                        + done.rows().size()
                        + " tables to the database storage.move-to names; a restart onto it completes the move.");
            }
            case MoveDatabase.Moved.Changed changed -> messages.send(sender, Keys.DATABASE_MOVE_CHANGED);
            case MoveDatabase.Moved.Refused refused ->
                messages.send(
                        sender,
                        switch (refused.why()) {
                            case NO_TARGET -> Keys.DATABASE_MOVE_NO_TARGET;
                            case PLAYERS_ONLINE -> Keys.DATABASE_MOVE_PLAYERS_ONLINE;
                            case TARGET_HOLDS_ROWS -> Keys.DATABASE_MOVE_TARGET_FULL;
                        });
        }
    }
}
