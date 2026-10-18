package com.uxplima.auth.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Whether a player is logged in, and how. Every answer is read from memory, so it may be asked on any thread,
 * a server thread among them. A player who is not on this server, or not logged in, answers {@code false} and
 * empty.
 */
public interface AuthApi {

    /** Whether the player with this id is logged in on this server or proxy. */
    boolean isAuthenticated(UUID player);

    /** How the player got in; empty while they are not logged in. */
    Optional<LoginWay> method(UUID player);

    /**
     * The kind of account the player is logged in to; empty while they are not logged in. A proxy knows the kind
     * the way in proves: a password or a registration is cracked, Mojang's word premium, Floodgate's Bedrock; after
     * a remembered session, staff, or a server's report it answers empty.
     */
    Optional<AccountType> kind(UUID player);
}
