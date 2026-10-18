package com.uxplima.auth.application.protection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.Countries;
import com.uxplima.auth.domain.protection.AddressRanges;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Countries are allowed or denied by the operator's list, with addresses let in whatever their country. */
final class CountryRuleTest {

    private static final String PLAY = "play.example.org";

    private boolean ready = true;
    private final Map<String, String> known = Map.of("203.0.113.7", "TR", "198.51.100.2", "DE");
    private final List<String> said = new ArrayList<>();
    private CountryRule.Settings settings = settings(CountryRule.Mode.ALLOW, true);

    private static CountryRule.Settings settings(CountryRule.Mode mode, boolean unknownAllowed) {
        return new CountryRule.Settings(
                mode, Set.of("TR"), new AddressRanges(List.of("198.51.100.0/24")), unknownAllowed);
    }

    private final Countries countries = new Countries() {
        @Override
        public boolean ready() {
            return ready;
        }

        @Override
        public Optional<String> of(String address) {
            return Optional.ofNullable(known.get(address));
        }
    };

    private Optional<Admission.Refused> arrive(String address) {
        return new CountryRule(countries, () -> settings, said::add).check(new Admission.Arrival(address, PLAY));
    }

    @Test
    @DisplayName("allow lets in only the listed countries, deny turns them away, off asks nothing")
    void allowAndDeny() {
        assertThat(arrive("203.0.113.7")).isEmpty();
        assertThat(arrive("192.0.2.9")).isEmpty();
        settings = new CountryRule.Settings(CountryRule.Mode.ALLOW, Set.of("TR"), new AddressRanges(List.of()), true);
        assertThat(arrive("198.51.100.2")).contains(new Admission.Refused(Keys.KICK_COUNTRY, Map.of("country", "DE")));

        settings = new CountryRule.Settings(CountryRule.Mode.DENY, Set.of("TR"), new AddressRanges(List.of()), true);
        assertThat(arrive("203.0.113.7")).isPresent();
        assertThat(arrive("198.51.100.2")).isEmpty();

        settings = new CountryRule.Settings(CountryRule.Mode.OFF, Set.of("TR"), new AddressRanges(List.of()), false);
        assertThat(arrive("198.51.100.2")).isEmpty();
        assertThat(arrive("192.0.2.9")).isEmpty();
    }

    @Test
    @DisplayName("an address on the allow list is let in whatever its country says")
    void theAllowListWins() {
        settings = new CountryRule.Settings(
                CountryRule.Mode.DENY, Set.of("DE"), new AddressRanges(List.of("198.51.100.0/24")), true);

        assertThat(arrive("198.51.100.2")).isEmpty();
    }

    @Test
    @DisplayName("a country the file does not know follows unknown: allowed, or turned away")
    void anUnknownCountry() {
        assertThat(arrive("192.0.2.9")).isEmpty();

        settings = settings(CountryRule.Mode.ALLOW, false);
        assertThat(arrive("192.0.2.9")).contains(new Admission.Refused(Keys.KICK_COUNTRY, Map.of("country", "?")));
    }

    @Test
    @DisplayName("with no country file every connection is let in, and the console is told once")
    void noFileLetsEveryoneIn() {
        ready = false;
        settings = settings(CountryRule.Mode.ALLOW, false);
        CountryRule rule = new CountryRule(countries, () -> settings, said::add);

        assertThat(rule.check(new Admission.Arrival("203.0.113.7", PLAY))).isEmpty();
        assertThat(rule.check(new Admission.Arrival("192.0.2.9", PLAY))).isEmpty();
        assertThat(said).singleElement().asString().contains(CountryRuleTest.FILE);
    }

    static final String FILE = "GeoLite2-Country.mmdb";
}
