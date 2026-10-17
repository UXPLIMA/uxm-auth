package com.uxplima.auth.velocity.adapter.inbound;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.state.AuthMethod;

/**
 * Where every player on the proxy stands: logged in, and how, or waiting with what the door decided. The proxy
 * is the one place a network keeps this, and each server learns it from the claim written before it connects.
 *
 * <p>The door's flags are kept for the whole visit: a player who logs out waits again under the same flags, so
 * a marked name stays marked and an unverified account stays unverified, whatever server they log out on.
 */
public final class ProxySessions {

    /**
     * A player's standing, whether a server has greeted their login yet, the door's flags, and whether this
     * proxy's door decided the player at all: one another plugin decided, as Floodgate decides a Bedrock player,
     * is greeted by the server's own door.
     */
    private record Session(Claim.Standing standing, boolean greeted, Claim.Waiting door, boolean ours) {}

    private static final Claim.Waiting NOTHING = new Claim.Waiting(false, false, false);

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    /** The player waits for a password, with the door's flags. */
    public void waiting(UUID id, Claim.Waiting flags) {
        Objects.requireNonNull(flags, "flags");
        sessions.put(Objects.requireNonNull(id, "id"), new Session(flags, false, flags, true));
    }

    /** Another plugin decided the player before the door: they wait, and a server's door takes their login. */
    public void untouched(UUID id) {
        sessions.put(Objects.requireNonNull(id, "id"), new Session(NOTHING, false, NOTHING, false));
    }

    /** The player logged in, by this method. */
    public void loggedIn(UUID id, AuthMethod method) {
        login(id, method, false);
    }

    /** A server reported that the player logged in there, by this method; that server already greeted it. */
    public void reported(UUID id, AuthMethod method) {
        login(id, method, true);
    }

    /** A server reported that the player waits again, after a logout: under the door's own flags. */
    public void loggedOut(UUID id) {
        Objects.requireNonNull(id, "id");
        sessions.computeIfPresent(
                id, (key, session) -> new Session(session.door(), false, session.door(), session.ours()));
    }

    private void login(UUID id, AuthMethod method, boolean greeted) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(method, "method");
        sessions.compute(
                id,
                (key, session) -> session == null
                        ? new Session(new Claim.LoggedIn(method), greeted, NOTHING, true)
                        : new Session(new Claim.LoggedIn(method), greeted, session.door(), session.ours()));
    }

    /**
     * What the claim to the next server says. A login is told by its own method once, so the first server greets
     * it; every server after that reads a login the network already greeted.
     */
    public Optional<Claim.Standing> nextStanding(UUID id) {
        Objects.requireNonNull(id, "id");
        Session[] told = new Session[1];
        sessions.computeIfPresent(id, (key, session) -> {
            told[0] = session;
            return session.standing() instanceof Claim.LoggedIn
                    ? new Session(session.standing(), true, session.door(), session.ours())
                    : session;
        });
        Session session = told[0];
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(session.greeted() ? new Claim.LoggedIn(AuthMethod.NETWORK) : session.standing());
    }

    /** What the door decided of the player, for as long as they are on the proxy. */
    public Optional<Claim.Waiting> door(UUID id) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(id, "id")))
                .map(Session::door);
    }

    /** Whether the player waits for a password the proxy itself takes. */
    public boolean waitsAtTheProxy(UUID id) {
        Session session = sessions.get(Objects.requireNonNull(id, "id"));
        return session != null && session.ours() && session.standing() instanceof Claim.Waiting;
    }

    /** Whether the player is logged in. */
    public boolean loggedIn(UUID id) {
        Session session = sessions.get(Objects.requireNonNull(id, "id"));
        return session != null && session.standing() instanceof Claim.LoggedIn;
    }

    /** The player left the proxy. */
    public void forget(UUID id) {
        sessions.remove(Objects.requireNonNull(id, "id"));
    }
}
