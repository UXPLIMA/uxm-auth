package com.uxplima.auth.application.protection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.protection.AddressRanges;
import com.uxplima.auth.domain.protection.SharedAddresses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The rules a connection meets at the door, before its name is read: the first that refuses answers. */
final class AdmissionTest {

    private static final Admission.Arrival FROM_HOME = new Admission.Arrival("203.0.113.7", "play.example.org");
    private static final Admission.Refused BANNED = new Admission.Refused(Keys.KICK_TIMEOUT, Map.of());

    private final List<String> asked = new ArrayList<>();

    private Admission.Rule rule(String name, Optional<Admission.Refused> answer) {
        return arrival -> {
            asked.add(name);
            return answer;
        };
    }

    @Test
    @DisplayName("with no rule that refuses, every connection is let in, and every rule was asked")
    void everyRuleLetsIn() {
        Admission admission = new Admission(
                SharedAddresses::none, List.of(rule("first", Optional.empty()), rule("second", Optional.empty())));

        assertThat(admission.decide(FROM_HOME)).isEmpty();
        assertThat(asked).containsExactly("first", "second");
    }

    @Test
    @DisplayName("the first rule that refuses answers, and the rules after it are not asked")
    void theFirstRefusalAnswers() {
        Admission admission = new Admission(
                SharedAddresses::none, List.of(rule("first", Optional.of(BANNED)), rule("second", Optional.empty())));

        assertThat(admission.decide(FROM_HOME)).contains(BANNED);
        assertThat(asked).containsExactly("first");
    }

    @Test
    @DisplayName("a connection through a shared-address filter is not judged by its address, a typed host alone is")
    void aSharedHostIsNotJudged() {
        Admission admission = new Admission(
                () -> new SharedAddresses(
                        new HostPatterns(List.of("*.tcpshield.com")), new AddressRanges(List.of("198.51.100.0/24"))),
                List.of(rule("first", Optional.of(BANNED))));

        assertThat(admission.decide(new Admission.Arrival("198.51.100.1", "mc.tcpshield.com:25565")))
                .isEmpty();
        assertThat(asked).isEmpty();
        assertThat(admission.decide(new Admission.Arrival("203.0.113.7", "mc.tcpshield.com:25565")))
                .contains(BANNED);
        assertThat(admission.decide(new Admission.Arrival("198.51.100.1", "play.example.org")))
                .contains(BANNED);
    }
}
