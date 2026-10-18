package com.uxplima.auth.application.protection;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.premium.HostPatterns;

/**
 * The rules a connection meets at the door, before its name is read: the country it comes from, a ban on its
 * address, how often the address connected. The first rule that refuses answers, and the ones after it are not
 * asked.
 *
 * <p>Through a host the operator lists as a shared-address filter, as TCPShield is, every player arrives from
 * the filter's address, so no rule that reads the address judges them.
 */
public final class Admission {

    /** A connection at the door: the address it comes from, and the host the player typed. */
    public record Arrival(String address, String host) {
        public Arrival {
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(host, "host");
        }
    }

    /**
     * Why a connection is turned away: the message, what fills its placeholders, and how long the refusal lasts,
     * nothing for one that does not run out.
     */
    public record Refused(Keys key, Map<String, String> values, Duration lasts) {
        public Refused {
            Objects.requireNonNull(key, "key");
            values = Map.copyOf(Objects.requireNonNull(values, "values"));
            Objects.requireNonNull(lasts, "lasts");
        }

        /** A refusal that does not run out. */
        public Refused(Keys key, Map<String, String> values) {
            this(key, values, Duration.ZERO);
        }
    }

    /** One rule of the door. */
    public interface Rule {
        Optional<Refused> check(Arrival arrival);
    }

    private final Supplier<HostPatterns> shared;
    private final List<Rule> rules;

    /** @param shared the hosts every player of which arrives from one address */
    public Admission(Supplier<HostPatterns> shared, List<Rule> rules) {
        this.shared = Objects.requireNonNull(shared, "shared");
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    /** A door with no rules: every connection is let in. */
    public static Admission none() {
        return new Admission(() -> new HostPatterns(List.of()), List.of());
    }

    /** Why this connection is turned away, or nothing when every rule lets it in. */
    public Optional<Refused> decide(Arrival arrival) {
        Objects.requireNonNull(arrival, "arrival");
        if (shared(arrival.host())) {
            return Optional.empty();
        }
        for (Rule rule : rules) {
            Optional<Refused> refused = rule.check(arrival);
            if (refused.isPresent()) {
                return refused;
            }
        }
        return Optional.empty();
    }

    /** Whether a player who typed this host arrives from a filter's address, which many players share. */
    public boolean shared(String host) {
        return shared.get().matches(Objects.requireNonNull(host, "host"));
    }
}
