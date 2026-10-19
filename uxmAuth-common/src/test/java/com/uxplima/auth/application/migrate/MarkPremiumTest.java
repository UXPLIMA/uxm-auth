package com.uxplima.auth.application.migrate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.auth.application.port.ImportMarks;
import com.uxplima.auth.application.port.ImportSource;
import com.uxplima.auth.application.port.ImportedAccount;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.testing.AuthFixture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A premium list read after the accounts turns the cracked accounts it names premium, and creates none. */
final class MarkPremiumTest {

    private static final UUID PAID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID OTHER = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");

    private final AuthFixture fixture = new AuthFixture(1);
    private final Map<String, Instant> marks = new ConcurrentHashMap<>();
    private final Map<String, PremiumAnswer> web = new HashMap<>();
    private final List<String> asked = new ArrayList<>();
    private final List<ImportedAccount> rows = new ArrayList<>();

    private final ImportMarks marked = new ImportMarks() {
        @Override
        public Optional<Instant> completed(String key) {
            return Optional.ofNullable(marks.get(key));
        }

        @Override
        public void complete(String key, Instant at) {
            marks.put(key, at);
        }
    };

    private final ImportSource source = new ImportSource() {
        @Override
        public String key() {
            return "fastlogin";
        }

        @Override
        public Stream<ImportedAccount> read() {
            return rows.stream();
        }

        @Override
        public void close() {}
    };

    @AfterEach
    void close() {
        fixture.close();
    }

    private MarkPremium marker(UuidScheme scheme) {
        return new MarkPremium(
                fixture.accounts,
                marked,
                () -> new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of()),
                name -> {
                    asked.add(name.key());
                    return web.getOrDefault(name.key(), new PremiumAnswer.Unknown());
                },
                scheme,
                fixture.clock);
    }

    private void row(String name, @Nullable UUID premiumId) {
        rows.add(new ImportedAccount(name, premiumId, premiumId, null, null, null, null, null));
    }

    private Account account(String name) {
        return fixture.accounts.findByName(new PlayerName(name)).orElseThrow();
    }

    @Test
    @DisplayName("a premium flag turns the cracked account premium under the scheme, and it keeps its password")
    void aFlagUpgrades() {
        fixture.registered("Steve", "hunter22");
        Account before = account("Steve");
        row("Steve", PAID);

        PremiumReport report = marker(UuidScheme.MOJANG).run(source);

        Account after = account("Steve");
        assertThat(after.kind()).isEqualTo(AccountKind.PREMIUM);
        assertThat(after.premiumId()).isEqualTo(PAID);
        assertThat(after.id()).isEqualTo(PAID);
        assertThat(after.password()).isEqualTo(before.password());
        assertThat(fixture.accounts.findById(before.id())).isEmpty();
        assertThat(report.marked()).isEqualTo(1);
        assertThat(asked)
                .as("a premium id only one name holds is not asked of the web")
                .isEmpty();
    }

    @Test
    @DisplayName("under keep-first an upgraded account keeps its id")
    void keepFirstKeepsTheId() {
        fixture.registered("Steve", "hunter22");
        UUID id = account("Steve").id();
        row("Steve", PAID);

        marker(UuidScheme.KEEP_FIRST).run(source);

        assertThat(account("Steve").id()).isEqualTo(id);
        assertThat(account("Steve").premiumId()).isEqualTo(PAID);
    }

    @Test
    @DisplayName("a premium id two names share is asked of the web, and only the name Mojang gives it is upgraded")
    void aSharedIdGoesToItsOwner() {
        fixture.registered("OldName", "hunter22");
        fixture.registered("NewName", "hunter22");
        row("OldName", PAID);
        row("NewName", PAID);
        web.put("oldname", new PremiumAnswer.Cracked());
        web.put("newname", new PremiumAnswer.Premium(PAID, "NewName"));

        PremiumReport report = marker(UuidScheme.OFFLINE).run(source);

        assertThat(account("NewName").kind()).isEqualTo(AccountKind.PREMIUM);
        assertThat(account("OldName").kind()).isEqualTo(AccountKind.CRACKED);
        assertThat(asked).doesNotHaveDuplicates().isSubsetOf("oldname", "newname");
        assertThat(report.marked()).isEqualTo(1);
        assertThat(report.notOwner()).isEqualTo(1);
    }

    @Test
    @DisplayName("a shared id the web cannot place leaves both accounts as they were, counted")
    void aSharedIdWithNoAnswerChangesNothing() {
        fixture.registered("OldName", "hunter22");
        fixture.registered("NewName", "hunter22");
        row("OldName", PAID);
        row("NewName", PAID);

        PremiumReport report = marker(UuidScheme.OFFLINE).run(source);

        assertThat(account("NewName").kind()).isEqualTo(AccountKind.CRACKED);
        assertThat(account("OldName").kind()).isEqualTo(AccountKind.CRACKED);
        assertThat(report.unanswered()).isEqualTo(2);
        assertThat(report.marked()).isZero();
    }

    @Test
    @DisplayName("a name no account holds is counted and never created, and a row with no flag is passed over")
    void unknownNamesAreCounted() {
        fixture.registered("Steve", "hunter22");
        row("Nobody", PAID);
        row("Steve", null);

        PremiumReport report = marker(UuidScheme.OFFLINE).run(source);

        assertThat(fixture.accounts.findByName(new PlayerName("Nobody"))).isEmpty();
        assertThat(account("Steve").kind()).isEqualTo(AccountKind.CRACKED);
        assertThat(report.unknownNames()).isEqualTo(1);
        assertThat(report.marked()).isZero();
    }

    @Test
    @DisplayName("an id another account holds already is counted, and the account stays cracked")
    void aTakenIdIsCounted() {
        fixture.registered("Steve", "hunter22");
        fixture.registered("Alex", "hunter22");
        row("Alex", OTHER);
        row("Steve", OTHER);
        web.put("alex", new PremiumAnswer.Premium(OTHER, "Alex"));
        marker(UuidScheme.OFFLINE).run(source);
        marks.clear();
        rows.clear();
        row("Steve", OTHER);

        PremiumReport report = marker(UuidScheme.OFFLINE).run(source);

        assertThat(account("Steve").kind()).isEqualTo(AccountKind.CRACKED);
        assertThat(report.taken()).isEqualTo(1);
    }

    @Test
    @DisplayName("an account that is premium already keeps its premium id, counted")
    void aPremiumAccountIsLeft() {
        fixture.registered("Steve", "hunter22");
        row("Steve", PAID);
        marker(UuidScheme.OFFLINE).run(source);
        marks.clear();
        rows.clear();
        row("Steve", OTHER);

        PremiumReport report = marker(UuidScheme.OFFLINE).run(source);

        assertThat(account("Steve").premiumId()).isEqualTo(PAID);
        assertThat(report.already()).isEqualTo(1);
        assertThat(report.marked()).isZero();
    }

    @Test
    @DisplayName("a list marked complete is not read again")
    void runsOnce() {
        fixture.registered("Steve", "hunter22");
        row("Steve", PAID);
        marker(UuidScheme.OFFLINE).run(source);
        fixture.registered("Alex", "hunter22");
        row("Alex", OTHER);

        PremiumReport second = marker(UuidScheme.OFFLINE).run(source);

        assertThat(second.alreadyDone()).isTrue();
        assertThat(account("Alex").kind()).isEqualTo(AccountKind.CRACKED);
        assertThat(marks).containsKey("fastlogin");
    }
}
