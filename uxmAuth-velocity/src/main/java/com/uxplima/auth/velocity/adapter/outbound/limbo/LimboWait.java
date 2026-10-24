package com.uxplima.auth.velocity.adapter.outbound.limbo;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.CaptchaImage;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.protection.CaptchaPicture;
import com.uxplima.auth.velocity.adapter.inbound.LimboVisit;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Holds a player who waits for the proxy's password in the waiting world, from the moment the login finishes
 * until they are let out logged in, run out of time, or leave. A player the door did not decide is not held: a
 * server's own door greets them, as it greets a Bedrock player with a form.
 *
 * <p>The join event is the one LimboAPI type named here: it sits in the same place in every generation. What does move
 * is behind {@link LimboKit}.
 */
public final class LimboWait {

    /** Makes the visit of one player, given who they wait as and what the world does for it. */
    public interface Visits {
        LimboVisit visit(Player player, Waiter waiter, LimboVisit.Exit exit);
    }

    /**
     * Who a held player waits as: the id and the name the door gives them. LimboAPI asks before the proxy's profile
     * event, so the player's own id may not be the one their account is kept under yet.
     */
    public record Waiter(UUID id, PlayerName name) {
        public Waiter {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }
    }

    private final LimboKit.Room room;
    private final Function<Player, Optional<Waiter>> waits;
    private final Visits visits;
    private final Supplier<AuthSettings.Timeouts> timeouts;
    private final Function<Player, Component> timeUp;
    private final Logger logger;

    /**
     * @param waits who the player waits as, or empty when they do not wait for the proxy's password
     * @param timeUp the reason a player whose time ran out reads
     */
    public LimboWait(
            LimboKit.Room room,
            Function<Player, Optional<Waiter>> waits,
            Visits visits,
            Supplier<AuthSettings.Timeouts> timeouts,
            Function<Player, Component> timeUp,
            Logger logger) {
        this.room = Objects.requireNonNull(room, "room");
        this.waits = Objects.requireNonNull(waits, "waits");
        this.visits = Objects.requireNonNull(visits, "visits");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
        this.timeUp = Objects.requireNonNull(timeUp, "timeUp");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Off the event thread: whether a player waits reads their account and their remembered session. */
    @Subscribe
    public EventTask onRegister(LoginLimboRegisterEvent event) {
        return EventTask.async(() -> register(event));
    }

    void register(LoginLimboRegisterEvent event) {
        Player player = event.getPlayer();
        waits.apply(player)
                .ifPresent(waiter -> event.addOnJoinCallback(() -> room.spawn(player, new Stay(player, waiter))));
    }

    /** One player's time in the world. LimboAPI calls it on the player's own connection thread. */
    private final class Stay implements LimboKit.Guest {

        private final Player player;
        private final Waiter waiter;
        private @Nullable LimboVisit visit;
        private @Nullable ScheduledFuture<?> deadline;
        private @Nullable ScheduledFuture<?> reminders;

        Stay(Player player, Waiter waiter) {
            this.player = player;
            this.waiter = waiter;
        }

        @Override
        public void arrive(LimboKit.Inside inWorld) {
            inWorld.stopFalling();
            ScheduledExecutorService loop = inWorld.loop();
            LimboVisit made = visits.visit(player, waiter, new WorldExit(player, inWorld, loop));
            visit = made;
            made.arrive();
            AuthSettings.Timeouts limits = timeouts.get();
            long limit = (made.registered() ? limits.login() : limits.register()).toMillis();
            deadline = loop.schedule(() -> made.timeUp(timeUp.apply(player)), limit, TimeUnit.MILLISECONDS);
            long every = limits.reminder().toMillis();
            reminders = loop.scheduleAtFixedRate(made::remind, every, every, TimeUnit.MILLISECONDS);
        }

        @Override
        public void said(String line) {
            LimboVisit made = visit;
            if (made != null) {
                made.said(line).exceptionally(failure -> {
                    logger.warn("A line typed in the waiting world could not be answered.", failure);
                    return null;
                });
            }
        }

        @Override
        public void left() {
            cancel(deadline);
            cancel(reminders);
            LimboVisit made = visit;
            if (made != null) {
                made.left();
            }
        }

        private void cancel(@Nullable ScheduledFuture<?> task) {
            if (task != null) {
                task.cancel(false);
            }
        }
    }

    /** What a visit does to the world: a line in chat, the way on, or the way out. */
    private record WorldExit(Player player, LimboKit.Inside inWorld, ScheduledExecutorService loop)
            implements LimboVisit.Exit {

        @Override
        public void tell(Component text) {
            player.sendMessage(text);
        }

        @Override
        public void release() {
            // The answer arrives on the hashing lane; the world is left from the player's own thread.
            loop.execute(inWorld::leave);
        }

        @Override
        public void sendAway(Component reason) {
            player.disconnect(reason);
        }

        @Override
        public void draw(CaptchaPicture picture) {
            inWorld.draw(CaptchaImage.of(picture));
        }
    }
}
