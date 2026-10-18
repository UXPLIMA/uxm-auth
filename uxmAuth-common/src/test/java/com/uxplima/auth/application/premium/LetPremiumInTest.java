package com.uxplima.auth.application.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.premium.ConflictStrategy;
import com.uxplima.auth.domain.premium.Refusal;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.InMemoryDisputes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A connection Mojang proved is let into the account it owns, and a name it proves changes hands as chosen. */
final class LetPremiumInTest {

    private static final UUID MOJANG = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final PlayerName NOTCH = new PlayerName("Notch");
    private static final String HERE = "10.0.0.7";

    private final AuthFixture fixture = new AuthFixture(1);
    private final InMemoryDisputes disputes = new InMemoryDisputes();
    private ConflictStrategy conflict = ConflictStrategy.RENAME;

    @AfterEach
    void close() {
        fixture.close();
    }

    private LetPremiumIn useCase(UuidScheme scheme) {
        return new LetPremiumIn(fixture.accounts, disputes, () -> conflict, () -> "_", scheme, fixture.clock);
    }

    private PremiumAdmission admit(PlayerName name, boolean ownerAsked) {
        return useCase(UuidScheme.KEEP_FIRST).admit(MOJANG, name, HERE, ownerAsked);
    }

    private Account cracked(String name) {
        Account account = AuthFixture.account(name, fixture.hashing.hash(Password.of("hunter22")));
        fixture.accounts.seed(account);
        return account;
    }

    private Account premium(String name, UUID mojangId) {
        Account account = new Account(
                UUID.randomUUID(),
                new PlayerName(name),
                AccountKind.PREMIUM,
                mojangId,
                null,
                AuthFixture.NOW,
                "10.0.0.1",
                null,
                null);
        fixture.accounts.seed(account);
        return account;
    }

    private static Account in(PremiumAdmission admission) {
        assertThat(admission).isInstanceOf(PremiumAdmission.In.class);
        PremiumAdmission.In in = (PremiumAdmission.In) admission;
        assertThat(in.state().method()).isEqualTo(AuthMethod.PREMIUM);
        return in.state().account();
    }

    @Test
    @DisplayName("a new paid name gets a premium account under the scheme's UUID, with no password, and is in")
    void aNewPaidNameGetsAnAccount() {
        Account made = in(admit(NOTCH, false));

        assertThat(made.id()).isEqualTo(MOJANG);
        assertThat(made.kind()).isEqualTo(AccountKind.PREMIUM);
        assertThat(made.premiumId()).isEqualTo(MOJANG);
        assertThat(made.password()).isNull();
        assertThat(fixture.accounts.findByName(NOTCH)).isPresent();
    }

    @Test
    @DisplayName("a returning paid player is let into the account their Mojang id proved, and the visit is recorded")
    void aReturningPlayerIsLetIn() {
        Account mine = premium("Notch", MOJANG);

        Account found = in(admit(NOTCH, false));

        assertThat(found.id()).isEqualTo(mine.id());
        assertThat(fixture.accounts.findById(mine.id()).orElseThrow().lastAddress())
                .isEqualTo(HERE);
        assertThat(fixture.accounts.findByName(NOTCH).orElseThrow().id()).isEqualTo(mine.id());
        assertThat(fixture.accounts.renamedFrom(NOTCH)).isEmpty();
    }

    @Test
    @DisplayName("a paid player who renamed at Mojang keeps their account and its UUID, which moves to the new name")
    void aRenamedPlayerKeepsTheirAccount() {
        Account mine = premium("OldName", MOJANG);

        Account found = in(admit(NOTCH, false));

        assertThat(found.id()).isEqualTo(mine.id());
        assertThat(found.name()).isEqualTo(NOTCH);
        assertThat(fixture.accounts.findByName(new PlayerName("OldName"))).isEmpty();
    }

    @Test
    @DisplayName("a name whose case Mojang changed follows Mojang's case")
    void theCaseFollowsMojang() {
        premium("notch", MOJANG);

        assertThat(in(admit(NOTCH, false)).name().value()).isEqualTo("Notch");
    }

    @Test
    @DisplayName("an owner who asked turns their cracked account premium and keeps its UUID under keep-first")
    void anOwnerTurnsTheirAccountPremium() {
        Account mine = cracked("Notch");

        Account turned = in(admit(NOTCH, true));

        assertThat(turned.id()).isEqualTo(mine.id());
        assertThat(turned.kind()).isEqualTo(AccountKind.PREMIUM);
        assertThat(turned.premiumId()).isEqualTo(MOJANG);
        assertThat(fixture.accounts.renamedFrom(NOTCH)).isEmpty();
    }

    @Test
    @DisplayName("under the mojang scheme an owner who turns premium moves to their Mojang UUID")
    void underMojangTheTurnMovesTheId() {
        Account mine = cracked("Notch");

        Account turned = in(useCase(UuidScheme.MOJANG).admit(MOJANG, NOTCH, HERE, true));

        assertThat(turned.id()).isEqualTo(MOJANG);
        assertThat(fixture.accounts.findById(mine.id())).isEmpty();
        assertThat(fixture.accounts.findById(MOJANG)).isPresent();
    }

    @Test
    @DisplayName("rename: the cracked holder moves to the marked name with everything it had, the owner gets the name")
    void renameMovesTheCrackedHolder() {
        Account holder = cracked("Notch");

        Account owner = in(admit(NOTCH, false));

        assertThat(owner.id()).isNotEqualTo(holder.id());
        assertThat(fixture.accounts.findById(holder.id()).orElseThrow().name()).isEqualTo(new PlayerName("Notch_"));
        assertThat(fixture.accounts.untoldRename(holder.id())).isPresent();
        assertThat(fixture.accounts.findByName(NOTCH).orElseThrow().id()).isEqualTo(owner.id());
    }

    @Test
    @DisplayName("block: both are stopped until staff settle it, and the cracked holder keeps everything meanwhile")
    void blockStopsBoth() {
        conflict = ConflictStrategy.BLOCK;
        Account holder = cracked("Notch");

        assertThat(admit(NOTCH, false)).isEqualTo(new PremiumAdmission.Refused(Refusal.NAME_DISPUTED));

        assertThat(disputes.of(NOTCH)).isPresent();
        assertThat(fixture.accounts.findByName(NOTCH).orElseThrow().id()).isEqualTo(holder.id());
    }

    @Test
    @DisplayName("a name in dispute stops the paid owner before anything else is read")
    void aDisputedNameStopsTheOwner() {
        premium("Notch", MOJANG);
        disputes.raise(NOTCH, MOJANG, AuthFixture.NOW);

        assertThat(admit(NOTCH, false)).isEqualTo(new PremiumAdmission.Refused(Refusal.NAME_DISPUTED));
    }

    @Test
    @DisplayName("a dispute staff settled for the cracked holder tells the paid owner to change their name; with the"
            + " holder gone it lets the owner in")
    void aKeptDisputeIsCrackedKeeps() {
        conflict = ConflictStrategy.BLOCK;
        Account holder = cracked("Notch");
        disputes.raise(NOTCH, MOJANG, AuthFixture.NOW);
        disputes.keep(NOTCH);

        assertThat(admit(NOTCH, false)).isEqualTo(new PremiumAdmission.Refused(Refusal.NAME_KEPT));
        assertThat(fixture.accounts.findByName(NOTCH).orElseThrow().id()).isEqualTo(holder.id());

        fixture.accounts.delete(holder.id());
        assertThat(admit(NOTCH, false)).isInstanceOf(PremiumAdmission.In.class);
        assertThat(disputes.of(NOTCH))
                .as("a settled dispute with nobody left to keep it is gone")
                .isEmpty();
    }

    @Test
    @DisplayName("cracked keeps: the paid owner is told to change their name, and nothing moves")
    void crackedKeeps() {
        conflict = ConflictStrategy.CRACKED_KEEPS;
        Account holder = cracked("Notch");

        assertThat(admit(NOTCH, false)).isEqualTo(new PremiumAdmission.Refused(Refusal.NAME_KEPT));
        assertThat(fixture.accounts.findByName(NOTCH).orElseThrow().id()).isEqualTo(holder.id());
    }

    @Test
    @DisplayName("premium takes: the cracked holder is deleted and the owner gets the name")
    void premiumTakes() {
        conflict = ConflictStrategy.PREMIUM_TAKES;
        Account holder = cracked("Notch");

        Account owner = in(admit(NOTCH, false));

        assertThat(fixture.accounts.findById(holder.id())).isEmpty();
        assertThat(fixture.accounts.findByName(NOTCH).orElseThrow().id()).isEqualTo(owner.id());
    }

    @Test
    @DisplayName("a premium account still under a name Mojang gave someone else moves away, and the new owner gets it")
    void aStalePremiumHolderMovesAway() {
        Account old = premium("Notch", UUID.randomUUID());

        Account owner = in(admit(NOTCH, false));

        assertThat(fixture.accounts.findById(old.id()).orElseThrow().name()).isEqualTo(new PlayerName("Notch_"));
        assertThat(owner.premiumId()).isEqualTo(MOJANG);
    }

    @Test
    @DisplayName("a paid player who renamed at Mojang onto a name a cracked account holds goes through the strategy")
    void aRenameOntoACrackedNameUsesTheStrategy() {
        Account mine = premium("OldName", MOJANG);
        Account holder = cracked("Notch");

        Account found = in(admit(NOTCH, false));

        assertThat(found.id()).isEqualTo(mine.id());
        assertThat(fixture.accounts.findById(holder.id()).orElseThrow().name()).isEqualTo(new PlayerName("Notch_"));
    }
}
