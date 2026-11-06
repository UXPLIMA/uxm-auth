package com.uxplima.auth.domain.premium;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Who a name belongs to at the door, under the rule the operator chose.
 *
 * <p>The web is asked only where the rule needs it, through a supplier the decision calls at most once: a name
 * a cracked player took first is not asked about under the automatic rule, and opt-in and the domain rule never
 * ask. A player who failed the handshake lately is never asked for it again until the memory of it ends.
 *
 * <p>Mojang's spelling of a paid name is the only one its owner's client ever sends. A name held here in another
 * spelling is therefore asked about when it arrives in Mojang's, so the paid owner can still prove it, and a new
 * name that differs from Mojang's spelling is not taken at all where the web was asked, because nobody under it
 * could ever pass the handshake.
 *
 * <p>Behind a shared-address filter a failure remembered for the address may have been another player's. There it
 * may let a connection in with a password, as it would anyway, but it never turns one away: the handshake is
 * asked again instead, so nobody behind the filter can lock a paid owner out by failing under their name.
 */
public final class NameOwnership {

    private final NameRule rule;
    private final WhenUnknown whenUnknown;
    private final HostPatterns premiumHosts;
    private final HostPatterns crackedHosts;
    private final boolean passwordFallback;

    public NameOwnership(NameRule rule, WhenUnknown whenUnknown, HostPatterns premiumHosts, HostPatterns crackedHosts) {
        this(rule, whenUnknown, premiumHosts, crackedHosts, true);
    }

    /**
     * @param passwordFallback whether a premium account that keeps a password logs in with it after a failed
     *     handshake: a Mojang outage, or a Mojang account its owner lost
     */
    public NameOwnership(
            NameRule rule,
            WhenUnknown whenUnknown,
            HostPatterns premiumHosts,
            HostPatterns crackedHosts,
            boolean passwordFallback) {
        this.passwordFallback = passwordFallback;
        this.rule = Objects.requireNonNull(rule, "rule");
        this.whenUnknown = Objects.requireNonNull(whenUnknown, "whenUnknown");
        this.premiumHosts = Objects.requireNonNull(premiumHosts, "premiumHosts");
        this.crackedHosts = Objects.requireNonNull(crackedHosts, "crackedHosts");
    }

    /** What the door does with this connection. The web is asked through {@code lookup}, if at all. */
    public Decision decide(Arrival arrival, Supplier<PremiumAnswer> lookup) {
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(lookup, "lookup");
        if (arrival.holder() == Holder.BEDROCK) {
            return new Decision.Cracked(false, false);
        }
        if (rule == NameRule.DOMAIN) {
            // Through the cracked address the player is the marked name, an account of its own: the holder of the
            // name they typed is somebody else's business. The premium address asks every name for the handshake,
            // and a failure there is told of the other address.
            return throughThePremiumAddress(arrival) ? domain(arrival) : new Decision.Cracked(false, false, true);
        }
        if (arrival.otherCase()) {
            return otherCase(arrival, lookup);
        }
        if (arrival.holder() == Holder.PREMIUM_WITH_PASSWORD && passwordFallback) {
            // Its password is the way in while Mojang cannot confirm the player, and the player is told why.
            return arrival.failedBefore() ? new Decision.Cracked(false, true) : new Decision.Handshake();
        }
        if (arrival.holder() == Holder.PREMIUM || arrival.holder() == Holder.PREMIUM_WITH_PASSWORD) {
            // An account that proved itself has no password to fall back on, or may not use it, under every
            // other rule. A try that failed lately is told why once, rather than shown "Invalid session" again.
            return refusedAfterFailure(arrival, Refusal.PAID_NAME);
        }
        return switch (rule) {
            case AUTOMATIC -> automatic(arrival, lookup);
            case OPT_IN -> optIn(arrival);
            case STRICT -> strict(arrival, lookup);
            case DOMAIN -> throw new IllegalStateException("the domain rule was decided above");
        };
    }

    private Decision automatic(Arrival arrival, Supplier<PremiumAnswer> lookup) {
        return switch (arrival.holder()) {
            case CRACKED -> asked(arrival);
            case NOBODY, CRACKED_UNVERIFIED, PREMIUM, PREMIUM_WITH_PASSWORD, BEDROCK ->
                arrival.failedBefore() ? afterFailure(arrival, lookup) : fromTheWeb(lookup.get(), arrival);
        };
    }

    /**
     * Under the automatic rule a player who failed lately joins with a password, but a new name in a spelling the
     * paid owner never sends is still not taken: the failure may have been under Mojang's spelling.
     */
    private Decision afterFailure(Arrival arrival, Supplier<PremiumAnswer> lookup) {
        if (arrival.holder() == Holder.NOBODY && misspelled(lookup.get(), arrival)) {
            return new Decision.Refuse(Refusal.PAID_SPELLING);
        }
        return new Decision.Cracked(false, true);
    }

    private static Decision optIn(Arrival arrival) {
        return switch (arrival.holder()) {
            case NOBODY -> new Decision.Cracked(false, false);
            case CRACKED, CRACKED_UNVERIFIED, PREMIUM, PREMIUM_WITH_PASSWORD, BEDROCK -> asked(arrival);
        };
    }

    /**
     * Every paid name passes the handshake. A cracked player never holds a paid name here, so a name nobody could
     * look up is never taken for a cracked one, whatever {@code when-every-source-fails} says: sources that are
     * down, or a Mojang cap spent by a flood of random names, are no way in. Such a name is refused until a source
     * answers, or asked for the handshake where the operator chose premium. A cracked account the web cleared
     * when it was made keeps the operator's choice, so an outage does not lock every such player out.
     */
    private Decision strict(Arrival arrival, Supplier<PremiumAnswer> lookup) {
        PremiumAnswer answer = lookup.get();
        if (answer instanceof PremiumAnswer.Unknown
                && arrival.holder() != Holder.CRACKED
                && whenUnknown != WhenUnknown.PREMIUM) {
            return new Decision.Refuse(Refusal.LOOKUP_FAILED);
        }
        Decision web = fromTheWeb(answer, arrival);
        if (web instanceof Decision.Handshake) {
            return refusedAfterFailure(arrival, Refusal.PAID_NAME);
        }
        return web;
    }

    /** Through the premium address, where the cracked one is decided before this. */
    private static Decision domain(Arrival arrival) {
        return refusedAfterFailure(arrival, Refusal.PREMIUM_ADDRESS);
    }

    /**
     * The handshake, or the refusal a failure lately earns. A failure remembered for a shared address may have
     * been another player's, so it turns nobody away there.
     */
    private static Decision refusedAfterFailure(Arrival arrival, Refusal why) {
        return arrival.failedBefore() && !arrival.shared() ? new Decision.Refuse(why) : new Decision.Handshake();
    }

    /** An address on both lists counts as cracked, and one on neither too. */
    private boolean throughThePremiumAddress(Arrival arrival) {
        return premiumHosts.matches(arrival.host()) && !crackedHosts.matches(arrival.host());
    }

    /** A cracked account whose player asked for the handshake gets it once; a failure leaves it as it was. */
    private static Decision asked(Arrival arrival) {
        if (arrival.asked() && !arrival.failedBefore()) {
            return new Decision.Handshake();
        }
        return new Decision.Cracked(false, arrival.failedBefore());
    }

    /**
     * An account holds the name in another spelling. Only the paid owner stands on a name in a spelling of their
     * own, and only in Mojang's: then the handshake decides, and once it passes the holder meets the operator's
     * conflict strategy. Any other spelling goes on to the pre-login, which says how the name is registered.
     */
    private static Decision otherCase(Arrival arrival, Supplier<PremiumAnswer> lookup) {
        if (arrival.failedBefore()) {
            return new Decision.Cracked(false, true);
        }
        return lookup.get() instanceof PremiumAnswer.Premium paid
                        && paid.exactName().equals(arrival.typed())
                ? new Decision.Handshake()
                : new Decision.Cracked(false, false);
    }

    /** Whether Mojang names a paid account under this name, spelled otherwise than the connection typed it. */
    private static boolean misspelled(PremiumAnswer answer, Arrival arrival) {
        return answer instanceof PremiumAnswer.Premium paid && !paid.exactName().equals(arrival.typed());
    }

    /**
     * A paid name in another spelling cannot be proved under the typed one. A new name is refused; a cracked
     * account that already holds it goes on with its password, now that the web has said who owns the name,
     * except under the strict rule, where a cracked player holds no paid name in any spelling.
     */
    private Decision misspelledName(Arrival arrival) {
        if (rule == NameRule.STRICT || arrival.holder() == Holder.NOBODY) {
            return new Decision.Refuse(Refusal.PAID_SPELLING);
        }
        return new Decision.Cracked(false, false);
    }

    private Decision fromTheWeb(PremiumAnswer answer, Arrival arrival) {
        if (misspelled(answer, arrival)) {
            return misspelledName(arrival);
        }
        return switch (answer) {
            case PremiumAnswer.Premium premium -> new Decision.Handshake();
            case PremiumAnswer.Cracked cracked -> new Decision.Cracked(false, false);
            case PremiumAnswer.Unknown unknown ->
                switch (whenUnknown) {
                    case CRACKED -> new Decision.Cracked(true, false);
                    case PREMIUM -> new Decision.Handshake();
                    case REFUSE -> new Decision.Refuse(Refusal.LOOKUP_FAILED);
                };
        };
    }
}
