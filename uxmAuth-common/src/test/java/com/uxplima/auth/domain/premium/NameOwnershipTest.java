package com.uxplima.auth.domain.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NameOwnershipTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private static PremiumAnswer paid() {
        return new PremiumAnswer.Premium(NOTCH, "Notch");
    }

    private static NameOwnership under(NameRule rule, WhenUnknown whenUnknown) {
        return new NameOwnership(rule, whenUnknown, new HostPatterns(List.of()), new HostPatterns(List.of()));
    }

    private static Arrival arriving(String typed, Holder holder, boolean otherCase) {
        return new Arrival(typed, holder, otherCase, false, false, "play.example.org");
    }

    @Test
    void thePaidSpellingOfANameHeldInAnotherCaseIsAskedForTheHandshake() {
        for (NameRule rule : List.of(NameRule.AUTOMATIC, NameRule.OPT_IN, NameRule.STRICT)) {
            Decision decision = under(rule, WhenUnknown.CRACKED)
                    .decide(arriving("Notch", Holder.CRACKED, true), NameOwnershipTest::paid);

            assertThat(decision).as(rule.name()).isEqualTo(new Decision.Handshake());
        }
    }

    @Test
    void anotherSpellingOfANameHeldInAnotherCaseGoesOnToThePreLogin() {
        Decision decision = under(NameRule.AUTOMATIC, WhenUnknown.CRACKED)
                .decide(arriving("NOTCH", Holder.CRACKED, true), NameOwnershipTest::paid);

        assertThat(decision).isEqualTo(new Decision.Cracked(false, false));
    }

    @Test
    void aNewNameSpelledOtherwiseThanMojangSpellsItIsNotTaken() {
        for (NameRule rule : List.of(NameRule.AUTOMATIC, NameRule.STRICT)) {
            Decision decision = under(rule, WhenUnknown.CRACKED)
                    .decide(arriving("notch", Holder.NOBODY, false), NameOwnershipTest::paid);

            assertThat(decision).as(rule.name()).isEqualTo(new Decision.Refuse(Refusal.PAID_SPELLING));
        }
    }

    @Test
    void aNewNameInMojangsSpellingIsStillAskedForTheHandshake() {
        Decision decision = under(NameRule.AUTOMATIC, WhenUnknown.CRACKED)
                .decide(arriving("Notch", Holder.NOBODY, false), NameOwnershipTest::paid);

        assertThat(decision).isEqualTo(new Decision.Handshake());
    }

    @Test
    void aFailureUnderMojangsSpellingDoesNotOpenAnotherSpelling() {
        Arrival failed = new Arrival("notch", Holder.NOBODY, false, false, true, "play.example.org");

        Decision decision = under(NameRule.AUTOMATIC, WhenUnknown.CRACKED).decide(failed, NameOwnershipTest::paid);

        assertThat(decision).isEqualTo(new Decision.Refuse(Refusal.PAID_SPELLING));
    }
}
