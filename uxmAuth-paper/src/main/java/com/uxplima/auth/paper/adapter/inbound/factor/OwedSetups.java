package com.uxplima.auth.paper.adapter.inbound.factor;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.domain.state.AuthState;

/**
 * The players held until a duty the operator requires is done, the app or the address, and what lets each of them
 * in then. Each duty has its own. The hold ends with the connection: a player who leaves owes it again at the next
 * login.
 */
public final class OwedSetups {

    private final Sessions sessions;
    private final Map<UUID, Runnable> waiting = new ConcurrentHashMap<>();

    public OwedSetups(Sessions sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /** Holds a logged-in player at setup; {@code proceed} lets them in once the app is on. */
    public void hold(Player player, AuthState.Authenticated in, Runnable proceed) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(proceed, "proceed");
        UUID id = player.getUniqueId();
        sessions.held(id).ifPresent(held -> {
            waiting.compute(id, (key, before) -> proceed);
            sessions.update(
                    id,
                    held.generation(),
                    state -> state.equals(in) ? new AuthState.SettingUp(in.account(), in.method()) : state);
        });
    }

    /**
     * The duty is done: a player this hold holds is logged in and let in; says whether one was. A player held by
     * another hold, or by none, is left as they are.
     */
    public boolean release(Player player) {
        Objects.requireNonNull(player, "player");
        UUID id = player.getUniqueId();
        Sessions.Held held = sessions.held(id).orElse(null);
        if (held == null) {
            return false;
        }
        AtomicReference<Runnable> proceed = new AtomicReference<>();
        waiting.compute(id, (key, before) -> {
            proceed.set(before);
            return null;
        });
        if (proceed.get() == null) {
            return false;
        }
        AtomicReference<AuthState.SettingUp> released = new AtomicReference<>();
        sessions.update(id, held.generation(), state -> {
            if (state instanceof AuthState.SettingUp setting) {
                released.set(setting);
                return setting.done();
            }
            return state;
        });
        if (released.get() == null) {
            return false;
        }
        proceed.get().run();
        return true;
    }

    /** The player left: nothing waits for them any more. */
    public void forget(UUID id) {
        waiting.compute(Objects.requireNonNull(id, "id"), (key, before) -> null);
    }
}
