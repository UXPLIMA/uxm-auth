package com.uxplima.auth.paper.adapter.inbound.hold;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;

/**
 * Greets a joining player once their client has said what language it reads.
 *
 * <p>The client sends its language a few ticks after the join, so a greeting drawn at the join itself reads
 * the server's default to a player whose client reads something else. The greeting waits for that report, or
 * for a moment's fallback when an English client never sends one, and runs once, whichever comes first.
 */
public final class Greeter {

    private final MomentRunner moments;
    private final BiConsumer<Player, Runnable> later;
    private final Predicate<Player> stillWaiting;
    private final Map<UUID, AuthSettings.Moment> waiting = new ConcurrentHashMap<>();

    /**
     * @param later runs the fallback a moment later on the player's own thread
     * @param stillWaiting whether the player still waits at the door, asked again when the greeting runs
     */
    public Greeter(MomentRunner moments, BiConsumer<Player, Runnable> later, Predicate<Player> stillWaiting) {
        this.moments = Objects.requireNonNull(moments, "moments");
        this.later = Objects.requireNonNull(later, "later");
        this.stillWaiting = Objects.requireNonNull(stillWaiting, "stillWaiting");
    }

    public void greet(Player player, AuthSettings.Moment moment) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(moment, "moment");
        waiting.put(player.getUniqueId(), moment);
        later.accept(player, () -> flush(player));
    }

    /** The client has said its language, so a greeting that waits can be drawn in it. */
    public void languageKnown(Player player) {
        flush(Objects.requireNonNull(player, "player"));
    }

    /** Drops a greeting that has not run, because the player got in or left first. */
    public void forget(Player player) {
        waiting.remove(Objects.requireNonNull(player, "player").getUniqueId());
    }

    /** Runs a waiting greeting, if the player is still the one at the door it was meant for. */
    private void flush(Player player) {
        AuthSettings.Moment moment = waiting.remove(player.getUniqueId());
        if (moment != null && player.isOnline() && stillWaiting.test(player)) {
            moments.fire(player, moment);
        }
    }
}
