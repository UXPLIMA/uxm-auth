package com.uxplima.auth.velocity.adapter.outbound.limbo;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.CaptchaImage;
import com.uxplima.auth.domain.protection.CaptchaPicture;
import com.uxplima.auth.velocity.adapter.inbound.LimboVisit;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent;
import net.elytrium.limboapi.api.world.player.LimboPlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Holds a player who waits for the proxy's password in the waiting world, from the moment the login finishes
 * until they are let out logged in, run out of time, or leave. A player the door did not decide is not held: a
 * server's own door greets them, as it greets a Bedrock player with a form.
 */
public final class LimboWait {

    /** Makes the visit of one player, given what the world does for it. */
    public interface Visits {
        LimboVisit visit(Player player, LimboVisit.Exit exit);
    }

    private final Limbo limbo;
    private final Predicate<UUID> waits;
    private final Visits visits;
    private final Supplier<AuthSettings.Timeouts> timeouts;
    private final Function<Player, Component> timeUp;
    private final Logger logger;

    /**
     * @param waits whether the player with this id waits for the proxy's password
     * @param timeUp the reason a player whose time ran out reads
     */
    public LimboWait(
            Limbo limbo,
            Predicate<UUID> waits,
            Visits visits,
            Supplier<AuthSettings.Timeouts> timeouts,
            Function<Player, Component> timeUp,
            Logger logger) {
        this.limbo = Objects.requireNonNull(limbo, "limbo");
        this.waits = Objects.requireNonNull(waits, "waits");
        this.visits = Objects.requireNonNull(visits, "visits");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
        this.timeUp = Objects.requireNonNull(timeUp, "timeUp");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Subscribe
    public void onRegister(LoginLimboRegisterEvent event) {
        register(event);
    }

    void register(LoginLimboRegisterEvent event) {
        Player player = event.getPlayer();
        if (waits.test(player.getUniqueId())) {
            event.addOnJoinCallback(() -> limbo.spawnPlayer(player, new Stay(player)));
        }
    }

    /** One player's time in the world. LimboAPI calls it on the player's own connection thread. */
    private final class Stay implements LimboSessionHandler {

        private final Player player;
        private @Nullable LimboVisit visit;
        private @Nullable ScheduledFuture<?> deadline;
        private @Nullable ScheduledFuture<?> reminders;

        Stay(Player player) {
            this.player = player;
        }

        @Override
        public void onSpawn(Limbo server, LimboPlayer inWorld) {
            inWorld.disableFalling();
            ScheduledExecutorService loop = inWorld.getScheduledExecutor();
            LimboVisit made = visits.visit(player, new WorldExit(player, inWorld, loop));
            visit = made;
            made.arrive();
            AuthSettings.Timeouts limits = timeouts.get();
            long limit = (made.registered() ? limits.login() : limits.register()).toMillis();
            deadline = loop.schedule(() -> made.timeUp(timeUp.apply(player)), limit, TimeUnit.MILLISECONDS);
            long every = limits.reminder().toMillis();
            reminders = loop.scheduleAtFixedRate(made::remind, every, every, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onChat(String line) {
            LimboVisit made = visit;
            if (made != null) {
                made.said(line).exceptionally(failure -> {
                    logger.warn("A line typed in the waiting world could not be answered.", failure);
                    return null;
                });
            }
        }

        @Override
        public void onDisconnect() {
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
    private record WorldExit(Player player, LimboPlayer inWorld, ScheduledExecutorService loop)
            implements LimboVisit.Exit {

        @Override
        public void tell(Component text) {
            player.sendMessage(text);
        }

        @Override
        public void release() {
            // The answer arrives on the hashing lane; the world is left from the player's own thread.
            loop.execute(inWorld::disconnect);
        }

        @Override
        public void sendAway(Component reason) {
            player.disconnect(reason);
        }

        @Override
        public void draw(CaptchaPicture picture) {
            inWorld.sendImage(CaptchaImage.of(picture));
        }
    }
}
