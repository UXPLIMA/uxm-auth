package com.uxplima.auth.application.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.ConflictStrategy;
import com.uxplima.auth.domain.premium.UuidScheme;
import org.junit.jupiter.api.Test;

class LetPremiumInTest {

    private static final UUID MOJANG = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final PlayerName NOTCH = new PlayerName("Notch");
    private static final String ADDRESS = "203.0.113.7";

    private final Clock clock = Clock.fixed(MemoryAccounts.THEN, ZoneOffset.UTC);
    private final MemoryAccounts accounts = new MemoryAccounts();

    private LetPremiumIn letIn(UuidScheme scheme) {
        return new LetPremiumIn(
                accounts, new MemoryAccounts.NoDisputes(), () -> ConflictStrategy.RENAME, () -> "_", scheme, clock);
    }

    private static Account in(PremiumAdmission admission) {
        assertThat(admission).isInstanceOf(PremiumAdmission.In.class);
        return ((PremiumAdmission.In) admission).state().account();
    }

    @Test
    void underTheOfflineSchemeTheOwnerGetsAnIdTheMovedSquatterDoesNotHold() {
        Account squatter = accounts.cracked(UuidScheme.offline(NOTCH), "Notch");

        Account owner = in(letIn(UuidScheme.OFFLINE).admit(MOJANG, NOTCH, ADDRESS, false));

        assertThat(owner.id()).isNotEqualTo(squatter.id());
        assertThat(owner.name()).isEqualTo(NOTCH);
        assertThat(accounts.findById(squatter.id()))
                .map(account -> account.name().value())
                .contains("Notch_");
        assertThat(accounts.findByPremiumId(MOJANG)).map(Account::id).contains(owner.id());
    }

    @Test
    void anOwnerWhoTurnedCrackedIsLetBackIntoTheirOwnAccount() {
        accounts.insert(
                new Account(MOJANG, NOTCH, AccountKind.CRACKED, null, null, MemoryAccounts.THEN, ADDRESS, null, null));

        Account owner = in(letIn(UuidScheme.KEEP_FIRST).admit(MOJANG, NOTCH, ADDRESS, false));

        assertThat(owner.id()).isEqualTo(MOJANG);
        assertThat(owner.kind()).isEqualTo(AccountKind.PREMIUM);
        assertThat(owner.premiumId()).isEqualTo(MOJANG);
        assertThat(accounts.all()).hasSize(1);
    }

    @Test
    void anOwnerWhoTurnedCrackedAndWasMovedAwayGetsTheirOwnAccountBack() {
        accounts.insert(new Account(
                MOJANG,
                new PlayerName("Notch_"),
                AccountKind.CRACKED,
                null,
                null,
                MemoryAccounts.THEN,
                ADDRESS,
                null,
                null));

        Account owner = in(letIn(UuidScheme.MOJANG).admit(MOJANG, NOTCH, ADDRESS, false));

        assertThat(owner.id()).isEqualTo(MOJANG);
        assertThat(owner.name()).isEqualTo(NOTCH);
        assertThat(accounts.all()).hasSize(1);
    }

    @Test
    void anAskedTurnToPremiumKeepsItsIdWhenTheMojangOneIsHeld() {
        Account holder = accounts.cracked(UuidScheme.offline(NOTCH), "Notch");
        accounts.insert(new Account(
                MOJANG,
                new PlayerName("Other"),
                AccountKind.PREMIUM,
                UUID.randomUUID(),
                null,
                MemoryAccounts.THEN,
                ADDRESS,
                null,
                null));

        Account owner = in(letIn(UuidScheme.MOJANG).admit(MOJANG, NOTCH, ADDRESS, true));

        assertThat(owner.id()).isEqualTo(holder.id());
        assertThat(owner.premiumId()).isEqualTo(MOJANG);
    }
}
