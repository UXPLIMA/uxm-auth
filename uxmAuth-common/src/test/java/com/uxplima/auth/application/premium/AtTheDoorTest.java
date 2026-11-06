package com.uxplima.auth.application.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.ConflictStrategy;
import com.uxplima.auth.domain.premium.Decision;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.premium.NameOwnership;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.Refusal;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.premium.WhenUnknown;
import com.uxplima.auth.domain.protection.AddressRanges;
import com.uxplima.auth.domain.protection.SharedAddresses;
import org.junit.jupiter.api.Test;

class AtTheDoorTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final String SQUATTER = "198.51.100.1";
    private static final String OWNER = "203.0.113.7";
    private static final String HOST = "play.example.org";

    private final Clock clock = Clock.fixed(MemoryAccounts.THEN, ZoneOffset.UTC);
    private final MemoryAccounts accounts = new MemoryAccounts();
    private PremiumAnswer web = new PremiumAnswer.Premium(NOTCH, "Notch");
    private int asked;

    private AtTheDoor door(NameRule rule) {
        return door(rule, 0, List.of());
    }

    /**
     * @param lookups the names one address may have the web asked about, 0 for no limit
     * @param filters the addresses of a shared-address filter
     */
    private AtTheDoor door(NameRule rule, int lookups, List<String> filters) {
        return new AtTheDoor(
                accounts,
                () -> new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of()),
                new PremiumLookup(
                        () -> List.of(name -> {
                            asked++;
                            return web;
                        }),
                        () -> Duration.ofMinutes(30),
                        clock),
                () -> new NameOwnership(
                        rule, WhenUnknown.CRACKED, new HostPatterns(List.of()), new HostPatterns(List.of())),
                new NameMemory(() -> Duration.ofMinutes(5), clock),
                new NameMemory(() -> Duration.ofMinutes(10), clock),
                new NameMemory(() -> Duration.ofMinutes(10), clock),
                new MemoryAccounts.NoDisputes(),
                () -> new SharedAddresses(new HostPatterns(List.of()), new AddressRanges(filters)),
                new LookupBudget(() -> lookups, clock));
    }

    private LetPremiumIn letIn(UuidScheme scheme) {
        return new LetPremiumIn(
                accounts, new MemoryAccounts.NoDisputes(), () -> ConflictStrategy.RENAME, () -> "_", scheme, clock);
    }

    @Test
    void thePaidOwnerOfANameSquattedInAnotherCaseProvesItAndTheSquatterMoves() {
        Account squatter = accounts.cracked(UUID.randomUUID(), "notch");

        Decision decision = door(NameRule.AUTOMATIC).decide("Notch", OWNER, HOST);
        PremiumAdmission admitted = letIn(UuidScheme.KEEP_FIRST).admit(NOTCH, new PlayerName("Notch"), OWNER, false);

        assertThat(decision).isEqualTo(new Decision.Handshake());
        assertThat(admitted).isInstanceOf(PremiumAdmission.In.class);
        assertThat(accounts.findByName(new PlayerName("Notch")))
                .map(Account::premiumId)
                .contains(NOTCH);
        assertThat(accounts.findById(squatter.id()))
                .map(account -> account.name().value())
                .contains("notch_");
    }

    @Test
    void theSquattersOwnSpellingStillReachesItsPassword() {
        accounts.cracked(UUID.randomUUID(), "notch");

        Decision decision = door(NameRule.AUTOMATIC).decide("notch", SQUATTER, HOST);

        assertThat(decision).isEqualTo(new Decision.Cracked(false, false));
    }

    @Test
    void aNewNameInAnotherSpellingThanMojangsIsRefused() {
        Decision decision = door(NameRule.AUTOMATIC).decide("notch", SQUATTER, HOST);

        assertThat(decision).isEqualTo(new Decision.Refuse(Refusal.PAID_SPELLING));
        assertThat(accounts.all()).isEmpty();
    }

    @Test
    void aPremiumAccountWhoseNameMojangRecasedFollowsIt() {
        accounts.insert(new Account(
                NOTCH,
                new PlayerName("notch"),
                AccountKind.PREMIUM,
                NOTCH,
                null,
                MemoryAccounts.THEN,
                OWNER,
                null,
                null));

        Decision decision = door(NameRule.AUTOMATIC).decide("Notch", OWNER, HOST);
        letIn(UuidScheme.KEEP_FIRST).admit(NOTCH, new PlayerName("Notch"), OWNER, false);

        assertThat(decision).isEqualTo(new Decision.Handshake());
        assertThat(accounts.findById(NOTCH))
                .map(account -> account.name().value())
                .isEqualTo(Optional.of("Notch"));
    }

    @Test
    void anAddressPastItsBudgetMeetsTheRuleForANameNobodyCouldLookUp() {
        web = new PremiumAnswer.Cracked();
        AtTheDoor automatic = door(NameRule.AUTOMATIC, 2, List.of());

        assertThat(automatic.decide("Alpha", SQUATTER, HOST)).isEqualTo(new Decision.Cracked(false, false));
        assertThat(automatic.decide("Bravo", SQUATTER, HOST)).isEqualTo(new Decision.Cracked(false, false));
        assertThat(automatic.decide("Charlie", SQUATTER, HOST)).isEqualTo(new Decision.Cracked(true, false));
        assertThat(asked).isEqualTo(2);
    }

    @Test
    void underTheStrictRuleAFloodOfNamesFromOneAddressIsRefusedNotLetIn() {
        web = new PremiumAnswer.Cracked();
        AtTheDoor strict = door(NameRule.STRICT, 1, List.of());

        strict.decide("Alpha", SQUATTER, HOST);

        assertThat(strict.decide("Notch", SQUATTER, HOST)).isEqualTo(new Decision.Refuse(Refusal.LOOKUP_FAILED));
        assertThat(strict.decide("Bravo", OWNER, HOST)).isEqualTo(new Decision.Cracked(false, false));
    }

    @Test
    void aNameTheWebAnsweredLatelyCostsNothing() {
        web = new PremiumAnswer.Cracked();
        AtTheDoor automatic = door(NameRule.AUTOMATIC, 1, List.of());

        automatic.decide("Alpha", SQUATTER, HOST);
        automatic.decide("Alpha", SQUATTER, HOST);

        assertThat(automatic.decide("Alpha", SQUATTER, HOST)).isEqualTo(new Decision.Cracked(false, false));
        assertThat(asked).isEqualTo(1);
    }

    @Test
    void aSharedAddressFilterIsHeldToNoBudget() {
        web = new PremiumAnswer.Cracked();
        AtTheDoor automatic = door(NameRule.AUTOMATIC, 1, List.of(SQUATTER));

        automatic.decide("Alpha", SQUATTER, HOST);

        assertThat(automatic.decide("Bravo", SQUATTER, HOST)).isEqualTo(new Decision.Cracked(false, false));
        assertThat(asked).isEqualTo(2);
    }

    @Test
    void aClaimThroughASharedAddressFilterIsNotTakenAndAskedOfNobody() {
        web = new PremiumAnswer.Premium(NOTCH, "Notch");
        accounts.cracked(UUID.randomUUID(), "Notch");
        AtTheDoor filtered = door(NameRule.AUTOMATIC, 0, List.of(SQUATTER));

        boolean taken = filtered.claim(SQUATTER, new PlayerName("Notch"));
        boolean owner = filtered.ownerAsks(SQUATTER, new PlayerName("Notch"));

        assertThat(taken).isFalse();
        assertThat(owner).isFalse();
        assertThat(filtered.decide("Notch", SQUATTER, HOST)).isEqualTo(new Decision.Cracked(false, false));
        assertThat(filtered.ownerAsked(SQUATTER, new PlayerName("Notch"))).isFalse();
    }

    @Test
    void aClaimFromAnAddressOfItsOwnAsksItsNextConnection() {
        accounts.cracked(UUID.randomUUID(), "Notch");
        AtTheDoor door = door(NameRule.AUTOMATIC);

        assertThat(door.claim(OWNER, new PlayerName("Notch"))).isTrue();
        assertThat(door.decide("Notch", OWNER, HOST)).isEqualTo(new Decision.Handshake());
        assertThat(door.decide("Notch", SQUATTER, HOST)).isEqualTo(new Decision.Cracked(false, false));
    }
}
