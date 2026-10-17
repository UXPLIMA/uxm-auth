package com.uxplima.auth.velocity.adapter.inbound;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.application.premium.AtTheDoor;
import com.uxplima.auth.application.premium.LetPremiumIn;
import com.uxplima.auth.application.premium.Moves;
import com.uxplima.auth.application.premium.PremiumAdmission;
import com.uxplima.auth.domain.name.NameVerdict;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.Decision;
import com.uxplima.auth.domain.premium.DomainMark;
import com.uxplima.auth.domain.premium.Refusal;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.state.AuthState;
import com.velocitypowered.api.util.GameProfile;

/**
 * The identity decision on the proxy. It asks the door a lone server asks; Velocity's own online mode is the
 * handshake, so a connection the door asks is put in online mode and one it lets through stays offline.
 *
 * <p>What the door decided is kept per connection, by its address, from the pre-login to the login, and let go
 * when the connection leaves or after a minute. A connection put in online mode that never reaches its profile
 * failed the handshake, and the next one under the name from that address is told so.
 */
public final class ProxyDoor {

    /** What Velocity does with the connection at its pre-login. */
    public sealed interface Gate {

        /** Ask the client for its key, as an online-mode proxy does. */
        record Online() implements Gate {}

        /** Let it in without a key, to the password. */
        record Offline() implements Gate {}

        /** Turn it away; {@code movedTo} names the account that moved away from the name, when one did. */
        record Refused(Refusal why, Optional<PlayerName> movedTo) implements Gate {
            public Refused {
                Objects.requireNonNull(why, "why");
                Objects.requireNonNull(movedTo, "movedTo");
            }
        }

        /** Turn it away for the name itself, as the operator's rules read it. */
        record BadName(NameVerdict verdict) implements Gate {
            public BadName {
                Objects.requireNonNull(verdict, "verdict");
            }
        }
    }

    /** What the proxy knows of a connection once it has a profile. */
    public sealed interface Arrival {

        /** Waiting for a password, unverified or told why its last try failed, under the mark when marked. */
        record Usual(boolean unverified, boolean explain, boolean marked) implements Arrival {}

        /** Logged in to this premium account. */
        record Premium(AuthState.Authenticated state) implements Arrival {
            public Premium {
                Objects.requireNonNull(state, "state");
            }
        }

        /** Proved, and turned away at the login for this reason. */
        record Refused(Refusal why) implements Arrival {
            public Refused {
                Objects.requireNonNull(why, "why");
            }
        }
    }

    /** What the door decided at the pre-login, kept until the profile. */
    private record Kept(Arrival arrival, AuthState state, Instant at) {}

    /** How long the proxy keeps what it decided for a connection that never logs in. */
    private static final Duration KEEP = Duration.ofMinutes(1);

    private final AtTheDoor door;
    private final LetPremiumIn premium;
    private final Moves moves;
    private final ReadTheName names;
    private final Supplier<DomainMark> mark;
    private final UuidScheme scheme;
    private final Clock clock;
    private final Map<InetSocketAddress, Kept> kept = new ConcurrentHashMap<>();
    private final Map<String, Instant> asked = new ConcurrentHashMap<>();

    public ProxyDoor(
            AtTheDoor door,
            LetPremiumIn premium,
            Moves moves,
            ReadTheName names,
            Supplier<DomainMark> mark,
            UuidScheme scheme,
            Clock clock) {
        this.door = Objects.requireNonNull(door, "door");
        this.premium = Objects.requireNonNull(premium, "premium");
        this.moves = Objects.requireNonNull(moves, "moves");
        this.names = Objects.requireNonNull(names, "names");
        this.mark = Objects.requireNonNull(mark, "mark");
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** What Velocity does with a connection under this name, from this address, through this host. It may wait. */
    public Gate preLogin(String name, InetSocketAddress remote, String host) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(host, "host");
        letGo();
        String ip = remote.getAddress().getHostAddress();
        if (asked.remove(key(ip, name)) != null) {
            door.failed(ip, new PlayerName(name));
        }
        return switch (door.decide(name, ip, host)) {
            case Decision.Handshake handshake -> {
                asked.put(key(ip, name), clock.instant());
                yield new Gate.Online();
            }
            case Decision.Cracked cracked -> offline(name, remote, cracked);
            case Decision.Refuse refuse ->
                new Gate.Refused(
                        refuse.why(),
                        refuse.why() == Refusal.PAID_NAME ? moves.movedTo(new PlayerName(name)) : Optional.empty());
        };
    }

    /** The profile this connection plays under, from the one Velocity made. It may wait on the database. */
    public GameProfile profile(GameProfile original, boolean online, InetSocketAddress remote) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(remote, "remote");
        if (online) {
            return proved(original, remote);
        }
        Kept mine = kept.get(remote);
        if (mine == null) {
            return original;
        }
        PlayerName name = nameOf(mine.state());
        UUID id = idOf(mine.state(), original.getId(), original.getName());
        if (id.equals(original.getId()) && name.value().equals(original.getName())) {
            return original;
        }
        return new GameProfile(id, name.value(), original.getProperties());
    }

    /** What the proxy knows of the connection at this address, once it has a profile. */
    public Optional<Arrival> arrival(InetSocketAddress remote) {
        return Optional.ofNullable(kept.get(Objects.requireNonNull(remote, "remote")))
                .map(Kept::arrival);
    }

    /** The connection at this address left. */
    public void gone(InetSocketAddress remote) {
        kept.remove(Objects.requireNonNull(remote, "remote"));
    }

    private Gate offline(String raw, InetSocketAddress remote, Decision.Cracked cracked) {
        ReadTheName.Reading reading = cracked.marked() ? names.readMarked(raw, mark.get()) : names.read(raw);
        if (reading instanceof ReadTheName.Reading.Refused refused) {
            return new Gate.BadName(refused.verdict());
        }
        AuthState state = ((ReadTheName.Reading.Opens) reading).state();
        kept.put(
                remote,
                new Kept(
                        new Arrival.Usual(cracked.unverified(), cracked.explain(), cracked.marked()),
                        state,
                        clock.instant()));
        return new Gate.Offline();
    }

    /** Mojang proved the name: the account it owns, under the account's id and Mojang's skin. */
    private GameProfile proved(GameProfile original, InetSocketAddress remote) {
        String ip = remote.getAddress().getHostAddress();
        PlayerName name = new PlayerName(original.getName());
        asked.remove(key(ip, original.getName()));
        door.passed(ip, name);
        switch (premium.admit(original.getId(), name, ip, door.ownerAsked(ip, name))) {
            case PremiumAdmission.In in -> {
                kept.put(remote, new Kept(new Arrival.Premium(in.state()), in.state(), clock.instant()));
                return new GameProfile(in.state().account().id(), original.getName(), original.getProperties());
            }
            case PremiumAdmission.Refused refused -> {
                kept.put(
                        remote,
                        new Kept(
                                new Arrival.Refused(refused.why()), new AuthState.Unregistered(name), clock.instant()));
                return original;
            }
        }
    }

    private static PlayerName nameOf(AuthState state) {
        return state instanceof AuthState.Registered registered
                ? registered.account().name()
                : ((AuthState.Unregistered) state).name();
    }

    /** An account's own id; a new name the scheme's, which an offline proxy already gave an unmarked one. */
    private UUID idOf(AuthState state, UUID given, String typed) {
        if (state instanceof AuthState.Registered registered) {
            return registered.account().id();
        }
        PlayerName name = ((AuthState.Unregistered) state).name();
        boolean derived = scheme != UuidScheme.RANDOM && name.value().equals(typed);
        return derived ? given : scheme.idFor(name, Optional.empty(), Optional.empty());
    }

    private void letGo() {
        Instant before = clock.instant().minus(KEEP);
        kept.values().removeIf(entry -> entry.at().isBefore(before));
        asked.values().removeIf(at -> at.isBefore(before));
    }

    private static String key(String ip, String name) {
        return ip + "|" + name.toLowerCase(Locale.ROOT);
    }
}
