package com.uxplima.auth.paper.adapter.inbound.hold;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * How long a waiting player may wait, and how often they are reminded.
 *
 * <p>One timer per waiting player, on that player's own thread, once a second. It stops by itself as soon as
 * the player is logged in or gone, so nothing runs for a player who is no longer waiting.
 */
public final class WaitingClock {

    private static final Duration BEAT = Duration.ofSeconds(1);

    private final Scheduler scheduler;
    private final Sessions sessions;
    private final Supplier<AuthSettings.Timeouts> timeouts;
    private final Clock clock;
    private final MomentRunner moments;
    private final Messages messages;

    public WaitingClock(
            Scheduler scheduler,
            Sessions sessions,
            Supplier<AuthSettings.Timeouts> timeouts,
            Clock clock,
            MomentRunner moments,
            Messages messages) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.moments = Objects.requireNonNull(moments, "moments");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Starts the clock for a player who has just arrived and waits. */
    public void start(Player player) {
        Objects.requireNonNull(player, "player");
        Instant arrived = clock.instant();
        AtomicReference<Instant> reminded = new AtomicReference<>(arrived);
        scheduler.entityTimer(player, BEAT, BEAT, handle -> beat(player, arrived, reminded, handle));
    }

    private void beat(Player player, Instant arrived, AtomicReference<Instant> reminded, TaskHandle handle) {
        AuthState state = sessions.state(player.getUniqueId()).orElse(null);
        if (!player.isOnline() || state == null || state instanceof AuthState.Authenticated) {
            handle.cancel();
            return;
        }
        boolean registering = state instanceof AuthState.Unregistered;
        AuthSettings.Timeouts limits = timeouts.get();
        Instant now = clock.instant();
        if (Duration.between(arrived, now).compareTo(registering ? limits.register() : limits.login()) >= 0) {
            handle.cancel();
            moments.fire(player, AuthSettings.Moment.TIMEOUT);
            player.kick(messages.render(player, Keys.KICK_TIMEOUT));
            return;
        }
        if (Duration.between(reminded.get(), now).compareTo(limits.reminder()) >= 0) {
            reminded.set(now);
            moments.fire(
                    player, registering ? AuthSettings.Moment.REMINDER_REGISTER : AuthSettings.Moment.REMINDER_LOGIN);
        }
    }
}
