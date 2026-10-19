package com.uxplima.auth.paper.adapter.inbound.account;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.entity.Player;

import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;

/** How a notice heard of off the player's thread reaches the player it is for, and only that player. */
public final class LoginNotices {

    private LoginNotices() {}

    /**
     * Finds the player under a name and tells them on their own thread, once that thread confirms they are still
     * logged in as the account. A player who left, or another connection under the name that waits at the door,
     * is told nothing.
     *
     * @param online the player under this name on this server, when there is one
     * @param loggedInAs whether this player is logged in as this account now
     */
    public record Reach(
            Function<PlayerName, @Nullable Player> online, Scheduler scheduler, BiPredicate<Player, UUID> loggedInAs) {
        public Reach {
            Objects.requireNonNull(online, "online");
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(loggedInAs, "loggedInAs");
        }

        /** Runs {@code tell} for the player of this account under this name, on their thread, if they are it. */
        public void tell(PlayerName name, UUID account, Consumer<Player> tell) {
            Player player = online.apply(Objects.requireNonNull(name, "name"));
            if (player == null) {
                return;
            }
            scheduler.entity(player, () -> {
                if (loggedInAs.test(player, account)) {
                    tell.accept(player);
                }
            });
        }
    }
}
