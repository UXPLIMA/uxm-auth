package com.uxplima.auth.paper.adapter.outbound.hold;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The players this plugin is moving right now. The hold stops every teleport of a waiting player, and these
 * are the ones it lets through: into the login world and back.
 */
public final class Passes {

    private final Set<UUID> moving = ConcurrentHashMap.newKeySet();

    public void allow(UUID player) {
        moving.add(Objects.requireNonNull(player, "player"));
    }

    public void done(UUID player) {
        moving.remove(Objects.requireNonNull(player, "player"));
    }

    /** Whether the teleport of this player is the plugin's own. */
    public boolean isPassing(UUID player) {
        return moving.contains(Objects.requireNonNull(player, "player"));
    }
}
