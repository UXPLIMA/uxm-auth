package com.uxplima.auth.paper.adapter.inbound.hold;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;

import com.uxplima.auth.adapter.outbound.AuthSettings.Moment;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.domain.state.AuthState;

/**
 * The boss bars an effect line put up while a player waited, taken down when the wait is over.
 *
 * <p>A bar stays its seconds. A player who logged in before them read "seconds to log in" counting down over a
 * game they were already playing. A bar shown once the player is in belongs to the operator's welcome and stays.
 */
public final class WaitingBars {

    private final Sessions sessions;

    /** The bars each waiting player sees; changed only through compute. */
    private final Map<UUID, List<BossBar>> shown = new ConcurrentHashMap<>();

    public WaitingBars(Sessions sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /** A login or a registration first takes down what the wait put up, then runs its own list. */
    public static MomentRunner endWithTheWait(MomentRunner runner, WaitingBars bars) {
        Objects.requireNonNull(runner, "runner");
        Objects.requireNonNull(bars, "bars");
        return (player, moment) -> {
            if (moment == Moment.LOGGED_IN || moment == Moment.REGISTERED) {
                bars.waitOver(player);
            }
            runner.fire(player, moment);
        };
    }

    /**
     * A bar went up on this player. It is kept only while they wait. Every player whose connection closed is
     * let go here too: their bars went with their client.
     */
    public void shown(Player player, BossBar bar) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(bar, "bar");
        for (UUID other : shown.keySet()) {
            shown.compute(other, (key, bars) -> sessions.state(key).isPresent() ? bars : null);
        }
        boolean waits = sessions.state(player.getUniqueId())
                .filter(state -> !(state instanceof AuthState.Authenticated))
                .isPresent();
        if (waits) {
            shown.compute(
                    player.getUniqueId(),
                    (key, bars) -> bars == null
                            ? List.of(bar)
                            : Stream.concat(bars.stream(), Stream.of(bar)).toList());
        }
    }

    /** The wait is over: every bar it put up comes down now. */
    void waitOver(Player player) {
        AtomicReference<List<BossBar>> taken = new AtomicReference<>(List.of());
        shown.compute(player.getUniqueId(), (key, bars) -> {
            if (bars != null) {
                taken.set(bars);
            }
            return null;
        });
        Objects.requireNonNull(taken.get(), "taken").forEach(player::hideBossBar);
    }

    /** How many players are remembered, for a test that proves they are let go. */
    int waiting() {
        return shown.size();
    }
}
