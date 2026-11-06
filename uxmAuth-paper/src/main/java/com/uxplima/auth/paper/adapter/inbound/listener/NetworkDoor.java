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
import com.uxplima.auth.domain.network.ClaimRefusal;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.state.AuthMethod;

/**
 * The proxy's word on a server behind it. The proxy writes a signed claim into the profile it forwards; this
 * reads it at the pre-login and takes it off the profile, so no client ever sees it.
 *
 * <p>A claim that does not hold, forged, stale, about another player, written for another server or a copy of
 * one taken before, turns the player away and says why in the console. A player who comes with no claim is asked
 * for the password as on a server that stands alone.
 */
public final class NetworkDoor {

    /** The profile property the claim travels in. */
    public static final String PROPERTY = Claims.PROPERTY;

    /** What the claim on a connection came to. */
    public sealed interface Reading {

        /** No network, or no claim: the usual door. */
        record None() implements Reading {}

        /** The proxy vouched for a login to this account, by this method, on the visit its claim named. */
        record LoggedIn(Account account, AuthMethod method, String visit) implements Reading {
            public LoggedIn {
                Objects.requireNonNull(account, "account");
                Objects.requireNonNull(method, "method");
                Objects.requireNonNull(visit, "visit");
            }
        }

        /**
         * The proxy saw this account's first step pass by this method, and the app's code is owed, on the visit its
         * claim named.
         */
        record Code(Account account, AuthMethod method, String visit) implements Reading {
            public Code {
                Objects.requireNonNull(account, "account");
                Objects.requireNonNull(method, "method");
                Objects.requireNonNull(visit, "visit");
            }
        }

        /** The player waits for a password, with the flags the proxy's door decided, on the visit its claim named. */
        record Waiting(Claim.Waiting flags, String visit) implements Reading {
            public Waiting {
                Objects.requireNonNull(flags, "flags");
                Objects.requireNonNull(visit, "visit");
            }
        }

        /** The claim did not hold, and the player is turned away. */
        record Refused() implements Reading {}
    }

    private static final NetworkDoor NONE = new NetworkDoor(
            Optional::empty,
            () -> "",
            id -> Optional.empty(),
            Clock.systemUTC(),
            Logger.getLogger(NetworkDoor.class.getName()));

    private final Supplier<Optional<Claims>> claims;
    private final Supplier<String> here;
    private final Function<UUID, Optional<Account>> accounts;
    private final Clock clock;
    private final Logger logger;

    /**
     * @param claims the reader of the proxy's claims, or empty while this server stands alone
     * @param here this server's name in the proxy's list, which a claim for it names
     */
    public NetworkDoor(
            Supplier<Optional<Claims>> claims,
            Supplier<String> here,
            Function<UUID, Optional<Account>> accounts,
            Clock clock,
            Logger logger) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.here = Objects.requireNonNull(here, "here");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** A server that stands alone: every connection goes to the usual door. */
    public static NetworkDoor none() {
        return NONE;
    }

    /**
     * What the claim on this connection comes to, with the claim taken off its profile. It comes off on a server
     * alone too: Paper shows a player's profile to every other player, and a claim there could be copied.
     */
    public Reading read(AsyncPlayerPreLoginEvent event) {
        Objects.requireNonNull(event, "event");
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
        Optional<Claims> reader = claims.get();
        if (reader.isEmpty()) {
            return new Reading.None();
        }
        return switch (reader.get()
                .read(
                        sealed.get(),
                        Claim.Direction.TO_SERVER,
                        event.getUniqueId(),
                        event.getName(),
                        here.get(),
                        clock.instant())) {
            case Claims.Reading.Refused refused -> refused(event.getName(), refused.why());
            case Claims.Reading.Valid valid -> standing(event.getName(), valid.claim());
        };
    }

    private Reading standing(String name, Claim claim) {
        return switch (claim.standing()) {
            case Claim.Waiting waiting -> new Reading.Waiting(waiting, claim.nonce());
            case Claim.LoggedIn in ->
                accounts.apply(claim.id())
                        .<Reading>map(account -> new Reading.LoggedIn(account, in.method(), claim.nonce()))
                        .orElseGet(() -> noAccount(name));
            case Claim.Code code ->
                accounts.apply(claim.id())
                        .<Reading>map(account -> new Reading.Code(account, code.method(), claim.nonce()))
                        .orElseGet(() -> noAccount(name));
        };
    }

    private Reading refused(String name, ClaimRefusal why) {
        logger.warning(
                "The proxy's claim for " + printable(name) + " was refused (" + why.name() + "). " + why.advice());
        return new Reading.Refused();
    }

    private Reading noAccount(String name) {
        logger.warning("The proxy's claim for " + printable(name) + " was refused (NO_ACCOUNT). Check that the proxy"
                + " and this server read the same database.");
        return new Reading.Refused();
    }

    private static String printable(String name) {
        return name.replaceAll("\\p{Cntrl}", "?");
    }
}
