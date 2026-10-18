package com.uxplima.auth.application.protection;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.Countries;
import com.uxplima.auth.domain.protection.AddressRanges;

/**
 * The door's rule on countries: the operator allows only the listed ones, or denies them, through the MaxMind
 * country file. An address on the allow list is let in whatever its country. With no file to ask, the rule lets
 * everybody in and says so once, because a rule that cannot answer must not keep a whole server out.
 */
public final class CountryRule implements Admission.Rule {

    /** What the list means. */
    public enum Mode {
        /** Countries are not asked. */
        OFF,
        /** Only the listed countries come in. */
        ALLOW,
        /** The listed countries stay out. */
        DENY
    }

    /**
     * The rule as the operator wrote it.
     *
     * @param mode what the list means
     * @param list the two-letter country codes, in upper case
     * @param allowAddresses the addresses and ranges let in whatever their country
     * @param unknownAllowed whether an address the file does not place comes in
     */
    public record Settings(Mode mode, Set<String> list, AddressRanges allowAddresses, boolean unknownAllowed) {
        public Settings {
            Objects.requireNonNull(mode, "mode");
            list = Objects.requireNonNull(list, "list").stream()
                    .map(code -> code.strip().toUpperCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
            Objects.requireNonNull(allowAddresses, "allowAddresses");
        }
    }

    private static final String UNKNOWN = "?";

    private final Countries countries;
    private final Supplier<Settings> settings;
    private final Consumer<String> console;
    private final AtomicBoolean toldNoFile = new AtomicBoolean();

    /** @param console where the rule says, once, that it has no file to ask */
    public CountryRule(Countries countries, Supplier<Settings> settings, Consumer<String> console) {
        this.countries = Objects.requireNonNull(countries, "countries");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.console = Objects.requireNonNull(console, "console");
    }

    @Override
    public Optional<Admission.Refused> check(Admission.Arrival arrival) {
        Objects.requireNonNull(arrival, "arrival");
        Settings now = settings.get();
        if (now.mode() == Mode.OFF || now.allowAddresses().contains(arrival.address())) {
            return Optional.empty();
        }
        if (!countries.ready()) {
            if (toldNoFile.compareAndSet(false, true)) {
                console.accept("protection.countries is on, but there is no GeoLite2-Country.mmdb in the data folder"
                        + " and no licence key to fetch one: every country is let in until there is.");
            }
            return Optional.empty();
        }
        Optional<String> country = countries.of(arrival.address());
        boolean comesIn = country.map(code -> now.list().contains(code) == (now.mode() == Mode.ALLOW))
                .orElse(now.unknownAllowed());
        return comesIn
                ? Optional.empty()
                : Optional.of(new Admission.Refused(Keys.KICK_COUNTRY, Map.of("country", country.orElse(UNKNOWN))));
    }
}
