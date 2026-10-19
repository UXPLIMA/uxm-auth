package com.uxplima.auth.adapter.outbound;

import java.util.Objects;

import com.uxplima.auth.domain.premium.NameRule;

/**
 * What the start says once the handshake is installed. Under opt-in nobody is proved until they ask, and an
 * operator who turned premium off by that rule read "on" and could not tell.
 */
public final class PremiumStartLine {

    private PremiumStartLine() {}

    /** The console line for this rule, with the door named as {@code door}: "the door" or "the proxy". */
    public static String of(NameRule rule, String door) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(door, "door");
        return rule == NameRule.OPT_IN
                ? "Premium login is ready: Mojang proves at " + door + " only a player who typed /premium."
                : "Premium auto-login is on: paid accounts are proved by Mojang at " + door + ".";
    }
}
