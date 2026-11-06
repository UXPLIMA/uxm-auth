package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.protection.Admission;
import com.uxplima.auth.domain.protection.SharedAddresses;
import org.junit.jupiter.api.Test;

class DoorRulingTest {

    private static final Admission.Arrival ARRIVAL = new Admission.Arrival("203.0.113.7", "play.example.net");

    @Test
    void aRuleThatCannotReadItsRecordsFailsInsteadOfLettingTheConnectionIn() {
        IllegalStateException lost = new IllegalStateException("the database is gone");
        Admission rules = admission(arrival -> {
            throw lost;
        });

        DoorListener.Ruling ruling = DoorListener.rule(rules, ARRIVAL);

        assertThat(ruling).isEqualTo(new DoorListener.Ruling.Failed(lost));
    }

    @Test
    void aRuleThatRefusesTurnsTheConnectionAway() {
        Admission.Refused banned = new Admission.Refused(Keys.KICK_BANNED, Map.of());
        Admission rules = admission(arrival -> Optional.of(banned));

        assertThat(DoorListener.rule(rules, ARRIVAL)).isEqualTo(new DoorListener.Ruling.Ruled(banned));
    }

    @Test
    void rulesThatAllLetTheConnectionInPassIt() {
        Admission rules = admission(arrival -> Optional.empty());

        assertThat(DoorListener.rule(rules, ARRIVAL)).isEqualTo(new DoorListener.Ruling.Passed());
    }

    private static Admission admission(Admission.Rule rule) {
        return new Admission(SharedAddresses::none, List.of(rule));
    }
}
