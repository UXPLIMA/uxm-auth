package com.uxplima.auth.paper.adapter.inbound.hold;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

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
    private final Map<UUID, AuthSettings.Moment> waiting = new ConcurrentHashMap<>();

    /** @param later runs the fallback a moment later on the player's own thread */
    public Greeter(MomentRunner moments, BiConsumer<Player, Runnable> later) {
        this.moments = Objects.requireNonNull(moments, "moments");
        this.later = Objects.requireNonNull(later, "later");
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

    private void flush(Player player) {
        AuthSettings.Moment moment = waiting.remove(player.getUniqueId());
        if (moment != null && player.isOnline()) {
            moments.fire(player, moment);
        }
    }
}
