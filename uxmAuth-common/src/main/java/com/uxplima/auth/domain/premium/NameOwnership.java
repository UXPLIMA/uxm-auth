package com.uxplima.auth.domain.premium;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Who a name belongs to at the door, under the rule the operator chose.
 *
 * <p>The web is asked only where the rule needs it, through a supplier the decision calls at most once: a name
 * a cracked player took first is not asked about under the automatic rule, and opt-in and the domain rule never
 * ask. A player who failed the handshake lately is never asked for it again until the memory of it ends.
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
        if (arrival.holder() == Holder.PREMIUM_WITH_PASSWORD && passwordFallback) {
            // Its password is the way in while Mojang cannot confirm the player, and the player is told why.
            return arrival.failedBefore() ? new Decision.Cracked(false, true) : new Decision.Handshake();
        }
        if (arrival.holder() == Holder.PREMIUM || arrival.holder() == Holder.PREMIUM_WITH_PASSWORD) {
            // An account that proved itself has no password to fall back on, or may not use it, under every
            // other rule. A try that failed lately is told why once, rather than shown "Invalid session" again.
            return arrival.failedBefore() ? new Decision.Refuse(Refusal.PAID_NAME) : new Decision.Handshake();
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
                arrival.failedBefore() ? new Decision.Cracked(false, true) : fromTheWeb(lookup.get());
        };
    }

    private static Decision optIn(Arrival arrival) {
        return switch (arrival.holder()) {
            case NOBODY -> new Decision.Cracked(false, false);
            case CRACKED, CRACKED_UNVERIFIED, PREMIUM, PREMIUM_WITH_PASSWORD, BEDROCK -> asked(arrival);
        };
    }

    private Decision strict(Arrival arrival, Supplier<PremiumAnswer> lookup) {
        Decision web = fromTheWeb(lookup.get());
        if (web instanceof Decision.Handshake && arrival.failedBefore()) {
            return new Decision.Refuse(Refusal.PAID_NAME);
        }
        return web;
    }

    /** Through the premium address, where the cracked one is decided before this. */
    private static Decision domain(Arrival arrival) {
        return arrival.failedBefore() ? new Decision.Refuse(Refusal.PREMIUM_ADDRESS) : new Decision.Handshake();
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

    private Decision fromTheWeb(PremiumAnswer answer) {
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
