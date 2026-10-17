package com.uxplima.auth.paper.adapter.inbound.listener;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.state.AuthMethod;

/**
 * The proxy's word on a server behind it. The proxy writes a signed claim into the profile it forwards; this
 * reads it at the pre-login and takes it off the profile, so no client ever sees it.
 *
 * <p>A claim that does not hold, forged, stale or about another player, turns the player away and says why in
 * the console. A player who comes with no claim is asked for the password as on a server that stands alone.
 */
public final class NetworkDoor {

    /** The profile property the claim travels in. */
    public static final String PROPERTY = Claims.PROPERTY;

    /** What the claim on a connection came to. */
    public sealed interface Reading {

        /** No network, or no claim: the usual door. */
        record None() implements Reading {}

        /** The proxy vouched for a login to this account, by this method. */
        record LoggedIn(Account account, AuthMethod method) implements Reading {
            public LoggedIn {
                Objects.requireNonNull(account, "account");
                Objects.requireNonNull(method, "method");
            }
        }

        /** The player waits for a password, with the flags the proxy's door decided. */
        record Waiting(Claim.Waiting flags) implements Reading {
            public Waiting {
                Objects.requireNonNull(flags, "flags");
            }
        }

        /** The claim did not hold, and the player is turned away. */
        record Refused() implements Reading {}
    }

    private static final NetworkDoor NONE = new NetworkDoor(
            Optional::empty, id -> Optional.empty(), Clock.systemUTC(), Logger.getLogger(NetworkDoor.class.getName()));

    private final Supplier<Optional<Claims>> claims;
    private final Function<UUID, Optional<Account>> accounts;
    private final Clock clock;
    private final Logger logger;

    /** @param claims the reader of the proxy's claims, or empty while this server stands alone */
    public NetworkDoor(
            Supplier<Optional<Claims>> claims, Function<UUID, Optional<Account>> accounts, Clock clock, Logger logger) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** A server that stands alone: every connection goes to the usual door. */
    public static NetworkDoor none() {
        return NONE;
    }

    /** What the claim on this connection comes to, with the claim taken off its profile. */
    public Reading read(AsyncPlayerPreLoginEvent event) {
        Objects.requireNonNull(event, "event");
        Optional<Claims> reader = claims.get();
        if (reader.isEmpty()) {
            return new Reading.None();
        }
        PlayerProfile profile = event.getPlayerProfile();
        Optional<String> sealed = profile.getProperties().stream()
                .filter(property -> property.getName().equals(PROPERTY))
                .map(ProfileProperty::getValue)
                .findFirst();
        if (sealed.isEmpty()) {
            return new Reading.None();
        }
        profile.removeProperty(PROPERTY);
        event.setPlayerProfile(profile);
        return switch (reader.get().read(sealed.get(), event.getUniqueId(), event.getName(), clock.instant())) {
            case Claims.Reading.Refused refused ->
                refused(event.getName(), refused.why().name());
            case Claims.Reading.Valid valid -> standing(event.getName(), valid.claim());
        };
    }

    private Reading standing(String name, Claim claim) {
        return switch (claim.standing()) {
            case Claim.Waiting waiting -> new Reading.Waiting(waiting);
            case Claim.LoggedIn in ->
                accounts.apply(claim.id())
                        .<Reading>map(account -> new Reading.LoggedIn(account, in.method()))
                        .orElseGet(() -> refused(name, "NO_ACCOUNT"));
        };
    }

    private Reading refused(String name, String why) {
        logger.warning("The proxy's claim for " + name.replaceAll("\\p{Cntrl}", "?") + " was refused (" + why
                + "). Check that network.secret here is the proxy's, and that the clocks agree.");
        return new Reading.Refused();
    }
}
