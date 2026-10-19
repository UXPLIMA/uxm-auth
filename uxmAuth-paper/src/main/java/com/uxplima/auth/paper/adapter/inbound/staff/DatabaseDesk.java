package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.migrate.ChangeScheme;
import com.uxplima.auth.application.migrate.MoveDatabase;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * What the console does to the database from {@code /uxmauth database}. Only the console: the work changes where
 * every account lives, so no setting opens it in game. One runs at a time, off the server's threads.
 */
public final class DatabaseDesk implements DatabaseActions {

    /** The names a scheme change names in full; the rest are counted. */
    private static final int NAMED = 20;

    private final MoveDatabase moves;
    private final ChangeScheme schemes;
    private final Executor work;
    private final Replies replies;
    private final Messages messages;
    private final Consumer<String> log;
    private final AtomicBoolean busy = new AtomicBoolean();

    /**
     * @param work runs the copy, off every server thread
     * @param log where the line a move leaves goes, which is the console
     */
    public DatabaseDesk(
            MoveDatabase moves,
            ChangeScheme schemes,
            Executor work,
            Replies replies,
            Messages messages,
            Consumer<String> log) {
        this.moves = Objects.requireNonNull(moves, "moves");
        this.schemes = Objects.requireNonNull(schemes, "schemes");
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
        return alone(sender, Keys.DATABASE_MOVE_STARTED, List.of(), moves::move, moved -> tell(sender, moved));
    }

    @Override
    public CompletableFuture<Void> scheme(CommandSender sender, String scheme) {
        Objects.requireNonNull(scheme, "scheme");
        if (!console(Objects.requireNonNull(sender, "sender"), "uuidscheme")) {
            return CompletableFuture.completedFuture(null);
        }
        Optional<UuidScheme> to = Arrays.stream(UuidScheme.values())
                .filter(known -> known.key().equalsIgnoreCase(scheme.trim()))
                .findFirst();
        if (to.isEmpty()) {
            String all = String.join(
                    ", ",
                    Arrays.stream(UuidScheme.values()).map(UuidScheme::key).toList());
            messages.send(
                    sender,
                    Keys.DATABASE_SCHEME_UNKNOWN,
                    Text.placeholder("scheme", scheme),
                    Text.placeholder("schemes", all));
            return CompletableFuture.completedFuture(null);
        }
        UuidScheme chosen = to.get();
        return alone(
                sender,
                Keys.DATABASE_SCHEME_STARTED,
                List.of(Text.placeholder("scheme", chosen.key())),
                () -> schemes.change(chosen),
                changed -> tell(sender, chosen, changed));
    }

    /** Runs the job off the server's threads unless another runs already, and hands its answer to the sender. */
    private <T> CompletableFuture<Void> alone(
            CommandSender sender, Keys started, List<TagResolver> values, Supplier<T> job, Consumer<T> then) {
        if (!busy.compareAndSet(false, true)) {
            messages.send(sender, Keys.DATABASE_MOVE_RUNNING);
            return CompletableFuture.completedFuture(null);
        }
        messages.send(sender, started, values.toArray(TagResolver[]::new));
        CompletableFuture<T> answer;
        try {
            answer = CompletableFuture.supplyAsync(job, work);
        } catch (RuntimeException refused) {
            // A refused hand-off must not leave every later job refused as running.
            busy.set(false);
            throw refused;
        }
        CompletableFuture<T> freed = answer.whenComplete((result, failure) -> busy.set(false));
        return replies.on(sender, freed, then);
    }

    private void tell(CommandSender sender, UuidScheme scheme, ChangeScheme.Changed changed) {
        switch (changed) {
            case ChangeScheme.Changed.PlayersOnline online ->
                messages.send(sender, Keys.DATABASE_SCHEME_PLAYERS_ONLINE);
            case ChangeScheme.Changed.Done done -> {
                messages.send(
                        sender,
                        Keys.DATABASE_SCHEME_DONE,
                        Text.placeholder("moved", String.valueOf(done.moved())),
                        Text.placeholder("unchanged", String.valueOf(done.unchanged())),
                        Text.placeholder("left", String.valueOf(done.left().size())),
                        Text.placeholder("scheme", scheme.key()));
                if (!done.left().isEmpty()) {
                    List<String> named =
                            done.left().subList(0, Math.min(NAMED, done.left().size()));
                    messages.send(
                            sender, Keys.DATABASE_SCHEME_LEFT, Text.placeholder("names", String.join(", ", named)));
                }
                log.accept(sender.getName() + " moved the accounts to the " + scheme.key() + " scheme: " + done.moved()
                        + " moved, " + done.unchanged() + " in place, "
                        + done.left().size() + " left.");
            }
        }
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
