package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * Staff made an account for a name: a player waiting here under it to register now waits to log in instead, with
 * the password staff gave them, and is told so on their own thread.
 */
public final class WaitingAccounts implements Consumer<Account> {

    private final Function<String, @Nullable Player> online;
    private final Sessions sessions;
    private final IntSupplier maxAttempts;
    private final Door door;
    private final Scheduler scheduler;
    private final Messages messages;

    /** @param online the player on this server under exactly this name, when there is one */
    public WaitingAccounts(
            Function<String, @Nullable Player> online,
            Sessions sessions,
            IntSupplier maxAttempts,
            Door door,
            Scheduler scheduler,
            Messages messages) {
        this.online = Objects.requireNonNull(online, "online");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.maxAttempts = Objects.requireNonNull(maxAttempts, "maxAttempts");
        this.door = Objects.requireNonNull(door, "door");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void accept(Account account) {
        Player player =
                online.apply(Objects.requireNonNull(account, "account").name().value());
        if (player == null || !player.getUniqueId().equals(account.id())) {
            return;
        }
        scheduler.entity(player, () -> {
            boolean waited = sessions.update(
                            player.getUniqueId(),
                            state -> state instanceof AuthState.Unregistered
                                    ? new AuthState.Registered(account, Attempts.fresh(maxAttempts.getAsInt()))
                                    : state)
                    .filter(AuthState.Registered.class::isInstance)
                    .isPresent();
            if (waited) {
                messages.send(player, Keys.STAFF_REGISTERED_YOU);
                door.hold(player);
            }
        });
    }
}
