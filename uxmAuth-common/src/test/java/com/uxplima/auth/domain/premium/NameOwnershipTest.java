package com.uxplima.auth.domain.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Who a name belongs to at the door, under each of the four rules. */
final class NameOwnershipTest {

    private static final PremiumAnswer PREMIUM =
            new PremiumAnswer.Premium(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), "Notch");
    private static final PremiumAnswer CRACKED = new PremiumAnswer.Cracked();
    private static final PremiumAnswer UNKNOWN = new PremiumAnswer.Unknown();

    private static final Decision HANDSHAKE = new Decision.Handshake();
    private static final Decision JOINS_CRACKED = new Decision.Cracked(false, false);
    private static final Decision JOINS_UNVERIFIED = new Decision.Cracked(true, false);
    private static final Decision JOINS_EXPLAINED = new Decision.Cracked(false, true);

    private final AtomicInteger asked = new AtomicInteger();

    private Supplier<PremiumAnswer> web(PremiumAnswer answer) {
        return () -> {
            asked.incrementAndGet();
            return answer;
        };
    }

    private static NameOwnership rule(NameRule rule) {
        return rule(rule, WhenUnknown.CRACKED);
    }

    private static NameOwnership rule(NameRule rule, WhenUnknown whenUnknown) {
        return new NameOwnership(
                rule,
                whenUnknown,
                new HostPatterns(List.of("premium.example.org", "*.paid.example.org")),
                new HostPatterns(List.of("cracked.example.org")));
    }

    private static Arrival arrival(Holder holder) {
        return new Arrival(holder, false, false, "play.example.org");
    }

    @Test
    @DisplayName("automatic: a new name Mojang knows is asked for the handshake, one it does not joins cracked")
    void automaticAsksANewPaidName() {
        NameOwnership automatic = rule(NameRule.AUTOMATIC);

        assertThat(automatic.decide(arrival(Holder.NOBODY), web(PREMIUM))).isEqualTo(HANDSHAKE);
        assertThat(automatic.decide(arrival(Holder.NOBODY), web(CRACKED))).isEqualTo(JOINS_CRACKED);
    }

    @Test
    @DisplayName("automatic: a player who failed the handshake joins cracked next time, and is told why")
    void automaticLetsAFailedPlayerInCracked() {
        Arrival failed = new Arrival(Holder.NOBODY, false, true, "play.example.org");

        assertThat(rule(NameRule.AUTOMATIC).decide(failed, web(PREMIUM))).isEqualTo(JOINS_EXPLAINED);
        assertThat(asked).hasValue(0);
    }

    @Test
    @DisplayName("automatic: a name a cracked player took first stays theirs, and the web is not asked")
    void automaticKeepsTheFirstComer() {
        assertThat(rule(NameRule.AUTOMATIC).decide(arrival(Holder.CRACKED), web(PREMIUM)))
                .isEqualTo(JOINS_CRACKED);
        assertThat(asked).hasValue(0);
    }

    @Test
    @DisplayName("automatic: a claim asks the next connection for the handshake, and a failed claim disturbs nobody")
    void automaticHonoursAClaim() {
        NameOwnership automatic = rule(NameRule.AUTOMATIC);

        assertThat(automatic.decide(new Arrival(Holder.CRACKED, true, false, "h"), web(PREMIUM)))
                .isEqualTo(HANDSHAKE);
        assertThat(automatic.decide(new Arrival(Holder.CRACKED, true, true, "h"), web(PREMIUM)))
                .isEqualTo(JOINS_EXPLAINED);
    }

    @Test
    @DisplayName("automatic: an account taken while every source was down is asked about again")
    void automaticAsksAgainAboutAnUnverifiedAccount() {
        NameOwnership automatic = rule(NameRule.AUTOMATIC);

        assertThat(automatic.decide(arrival(Holder.CRACKED_UNVERIFIED), web(PREMIUM)))
                .isEqualTo(HANDSHAKE);
        assertThat(automatic.decide(arrival(Holder.CRACKED_UNVERIFIED), web(UNKNOWN)))
                .isEqualTo(JOINS_UNVERIFIED);
    }

    @Test
    @DisplayName("every rule asks a premium account for the handshake, and lets a Bedrock account through")
    void everyRuleKnowsPremiumAndBedrockAccounts() {
        for (NameRule each : NameRule.values()) {
            assertThat(rule(each).decide(arrival(Holder.PREMIUM), web(UNKNOWN)))
                    .as(each.name())
                    .isEqualTo(HANDSHAKE);
        }
        for (NameRule each : NameRule.values()) {
            assertThat(rule(each).decide(arrival(Holder.BEDROCK), web(PREMIUM)))
                    .as(each.name())
                    .isEqualTo(JOINS_CRACKED);
        }
        assertThat(asked).hasValue(0);
    }

    @Test
    @DisplayName("when every source fails, the operator's choice applies: cracked and unverified, premium, or refused")
    void whenEverySourceFailsTheOperatorChooses() {
        assertThat(rule(NameRule.AUTOMATIC, WhenUnknown.CRACKED).decide(arrival(Holder.NOBODY), web(UNKNOWN)))
                .isEqualTo(JOINS_UNVERIFIED);
        assertThat(rule(NameRule.AUTOMATIC, WhenUnknown.PREMIUM).decide(arrival(Holder.NOBODY), web(UNKNOWN)))
                .isEqualTo(HANDSHAKE);
        assertThat(rule(NameRule.AUTOMATIC, WhenUnknown.REFUSE).decide(arrival(Holder.NOBODY), web(UNKNOWN)))
                .isEqualTo(new Decision.Refuse(Refusal.LOOKUP_FAILED));
        assertThat(rule(NameRule.STRICT, WhenUnknown.REFUSE).decide(arrival(Holder.CRACKED), web(UNKNOWN)))
                .isEqualTo(new Decision.Refuse(Refusal.LOOKUP_FAILED));
    }

    @Test
    @DisplayName("opt-in: everybody joins with a password and the web is never asked, until the player asks")
    void optInAsksOnlyThoseWhoAsked() {
        NameOwnership optIn = rule(NameRule.OPT_IN);

        assertThat(optIn.decide(arrival(Holder.NOBODY), web(PREMIUM))).isEqualTo(JOINS_CRACKED);
        assertThat(optIn.decide(arrival(Holder.CRACKED), web(PREMIUM))).isEqualTo(JOINS_CRACKED);
        assertThat(optIn.decide(new Arrival(Holder.CRACKED, true, false, "h"), web(PREMIUM)))
                .isEqualTo(HANDSHAKE);
        assertThat(optIn.decide(new Arrival(Holder.CRACKED, true, true, "h"), web(PREMIUM)))
                .isEqualTo(JOINS_EXPLAINED);
        assertThat(asked).hasValue(0);
    }

    @Test
    @DisplayName("strict: every name Mojang knows must pass the handshake, even one a cracked account holds")
    void strictAsksEveryPaidName() {
        NameOwnership strict = rule(NameRule.STRICT);

        assertThat(strict.decide(arrival(Holder.NOBODY), web(PREMIUM))).isEqualTo(HANDSHAKE);
        assertThat(strict.decide(arrival(Holder.CRACKED), web(PREMIUM))).isEqualTo(HANDSHAKE);
        assertThat(strict.decide(arrival(Holder.CRACKED), web(CRACKED))).isEqualTo(JOINS_CRACKED);
    }

    @Test
    @DisplayName("strict: a player who failed the handshake under a paid name is refused, and told why")
    void strictRefusesAFailedPlayer() {
        Arrival failed = new Arrival(Holder.NOBODY, false, true, "play.example.org");

        assertThat(rule(NameRule.STRICT).decide(failed, web(PREMIUM)))
                .isEqualTo(new Decision.Refuse(Refusal.PAID_NAME));
    }

    @Test
    @DisplayName("by domain: the premium address asks for the handshake, every other address joins cracked")
    void domainFollowsTheAddress() {
        NameOwnership domain = rule(NameRule.DOMAIN);

        assertThat(domain.decide(new Arrival(Holder.NOBODY, false, false, "Premium.Example.org"), web(CRACKED)))
                .isEqualTo(HANDSHAKE);
        assertThat(domain.decide(new Arrival(Holder.NOBODY, false, false, "eu.paid.example.org"), web(CRACKED)))
                .isEqualTo(HANDSHAKE);
        assertThat(domain.decide(new Arrival(Holder.NOBODY, false, false, "10.0.0.1"), web(PREMIUM)))
                .isEqualTo(JOINS_CRACKED);
        assertThat(asked).hasValue(0);
    }

    @Test
    @DisplayName(
            "by domain: a premium account joining through the cracked address still proves itself, never a password")
    void domainNeverHandsAPremiumAccountToAPassword() {
        Arrival throughTheCrackedAddress = new Arrival(Holder.PREMIUM, false, false, "cracked.example.org");

        assertThat(rule(NameRule.DOMAIN).decide(throughTheCrackedAddress, web(PREMIUM)))
                .isEqualTo(HANDSHAKE);
    }

    @Test
    @DisplayName("by domain: a failed handshake on the premium address is refused, and told to use the other one")
    void domainRefusesAFailedPremiumAddress() {
        Arrival failed = new Arrival(Holder.NOBODY, false, true, "premium.example.org");

        assertThat(rule(NameRule.DOMAIN).decide(failed, web(PREMIUM)))
                .isEqualTo(new Decision.Refuse(Refusal.PREMIUM_ADDRESS));
    }

    @Test
    @DisplayName("an address pattern matches the host without its port and in any case, and a star only one label")
    void hostPatterns() {
        HostPatterns patterns = new HostPatterns(List.of("*.paid.example.org", "play.example.org"));

        assertThat(patterns.matches("EU.paid.example.org")).isTrue();
        assertThat(patterns.matches("play.example.org:25565")).isTrue();
        assertThat(patterns.matches("play.example.org.")).isTrue();
        assertThat(patterns.matches("play.example.org\u0000FML3\u0000")).isTrue();
        assertThat(patterns.matches("a.b.paid.example.org")).isFalse();
        assertThat(patterns.matches("paidXexample.org")).isFalse();
        assertThat(patterns.matches("evil-play.example.org")).isFalse();
    }
}
