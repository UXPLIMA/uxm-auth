package com.uxplima.auth.adapter.outbound.geo;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.auth.application.protection.CountryRule;

/**
 * The countries block: the rule, the MaxMind licence that fetches the file, and how old the file may grow.
 *
 * @param rule what the door does with a country
 * @param licence the operator's MaxMind account and licence key, empty to use a file dropped in by hand
 * @param refresh how old the file may grow before it is fetched again
 */
public record GeoSettings(CountryRule.Settings rule, Licence licence, Duration refresh) {
    public GeoSettings {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(licence, "licence");
        Objects.requireNonNull(refresh, "refresh");
        if (refresh.isNegative() || refresh.isZero()) {
            throw new IllegalArgumentException("The refresh must be longer than nothing");
        }
    }

    /** The MaxMind account and its licence key. Neither is ever written out, not even in this record's text. */
    public record Licence(String accountId, String key) {
        public Licence {
            accountId = Objects.requireNonNull(accountId, "accountId").strip();
            key = Objects.requireNonNull(key, "key").strip();
        }

        /** Whether both are written. */
        public boolean present() {
            return !accountId.isEmpty() && !key.isEmpty();
        }

        @Override
        public String toString() {
            return present() ? "Licence[set]" : "Licence[none]";
        }
    }
}
