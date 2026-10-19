package com.uxplima.auth.application.migrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.port.SchemeRecord;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.testing.InMemoryAccounts;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every account takes the id another scheme gives it, or is named and left where it cannot. */
final class ChangeSchemeTest {

    private static final UUID MOJANG = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final Instant MADE = Instant.parse("2026-01-01T00:00:00Z");

    private final List<String> done = new ArrayList<>();
    private int online;
    private boolean failing;

    private final InMemoryAccounts accounts = new InMemoryAccounts() {
        @Override
        public synchronized void moveId(UUID from, UUID to) {
            if (failing) {
                throw new IllegalStateException("the database went away");
            }
            done.add("move "
                    + findById(from).map(account -> account.name().value()).orElse("?"));
            super.moveId(from, to);
        }
    };

    private UuidScheme recorded = UuidScheme.OFFLINE;

    private final SchemeRecord record = new SchemeRecord() {
        @Override
        public Optional<UuidScheme> recorded() {
            return Optional.of(recorded);
        }

        @Override
        public void replace(UuidScheme scheme) {
            done.add("record " + scheme.key());
            recorded = scheme;
        }
    };

    private ChangeScheme change() {
        return new ChangeScheme(accounts, accounts, record, () -> online);
    }

    private static UUID offline(String name) {
        return UuidScheme.offline(new PlayerName(name));
    }

    private void account(String name, UUID id, AccountKind kind, @Nullable UUID premiumId) {
        accounts.seed(new Account(id, new PlayerName(name), kind, premiumId, null, MADE, "", null, null));
    }

    private UUID idOf(String name) {
        return accounts.findByName(new PlayerName(name)).orElseThrow().id();
    }

    @Test
    @DisplayName("offline to mojang moves the premium accounts only, and records the scheme last")
    void offlineToMojangMovesPremiumOnly() {
        account("Steve", offline("Steve"), AccountKind.CRACKED, null);
        account("Notch", offline("Notch"), AccountKind.PREMIUM, MOJANG);
        account("Bedrock_Jo", UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6"), AccountKind.BEDROCK, null);

        ChangeScheme.Changed changed = change().change(UuidScheme.MOJANG);

        assertThat(changed).isEqualTo(new ChangeScheme.Changed.Done(1, 2, List.of()));
        assertThat(idOf("Notch")).isEqualTo(MOJANG);
        assertThat(idOf("Steve")).isEqualTo(offline("Steve"));
        assertThat(idOf("Bedrock_Jo")).isEqualTo(UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6"));
        assertThat(done).containsExactly("move Notch", "record mojang");
    }

    @Test
    @DisplayName("an account whose new id another account takes first moves once that one has moved away")
    void aChainResolves() {
        // Zed was renamed from Notch, and kept the id Notch's name derives. Notch comes first, and waits for Zed.
        account("Zed", offline("Notch"), AccountKind.CRACKED, null);
        account("Notch", MOJANG, AccountKind.PREMIUM, MOJANG);
        recorded = UuidScheme.MOJANG;

        ChangeScheme.Changed changed = change().change(UuidScheme.OFFLINE);

        assertThat(changed).isEqualTo(new ChangeScheme.Changed.Done(2, 0, List.of()));
        assertThat(idOf("Zed")).isEqualTo(offline("Zed"));
        assertThat(idOf("Notch")).isEqualTo(offline("Notch"));
        assertThat(done).containsExactly("move Zed", "move Notch", "record offline");
    }

    @Test
    @DisplayName("two accounts that would trade ids are named and left, and the rest still move")
    void aCollisionIsNamedAndLeft() {
        account("Alex", offline("Bob"), AccountKind.CRACKED, null);
        account("Bob", offline("Alex"), AccountKind.CRACKED, null);
        account("Notch", MOJANG, AccountKind.PREMIUM, MOJANG);
        recorded = UuidScheme.MOJANG;

        ChangeScheme.Changed changed = change().change(UuidScheme.OFFLINE);

        assertThat(changed).isEqualTo(new ChangeScheme.Changed.Done(1, 0, List.of("Alex", "Bob")));
        assertThat(idOf("Alex")).isEqualTo(offline("Bob"));
        assertThat(idOf("Notch")).isEqualTo(offline("Notch"));
        assertThat(recorded).isEqualTo(UuidScheme.OFFLINE);
    }

    @Test
    @DisplayName("two accounts the scheme gives one id are both left, because either one taking it would be a guess")
    void aSharedIdIsLeftToBoth() {
        account("Notch", offline("Notch"), AccountKind.PREMIUM, MOJANG);
        account("Notch_Old", offline("Notch_Old"), AccountKind.PREMIUM, MOJANG);

        ChangeScheme.Changed changed = change().change(UuidScheme.MOJANG);

        assertThat(changed).isEqualTo(new ChangeScheme.Changed.Done(0, 0, List.of("Notch", "Notch_Old")));
        assertThat(idOf("Notch")).isEqualTo(offline("Notch"));
        assertThat(idOf("Notch_Old")).isEqualTo(offline("Notch_Old"));
    }

    @Test
    @DisplayName("a second run moves nothing")
    void aRerunMovesNothing() {
        account("Notch", offline("Notch"), AccountKind.PREMIUM, MOJANG);
        change().change(UuidScheme.MOJANG);
        done.clear();

        assertThat(change().change(UuidScheme.MOJANG)).isEqualTo(new ChangeScheme.Changed.Done(0, 1, List.of()));
        assertThat(done).containsExactly("record mojang");
    }

    @Test
    @DisplayName("keep-first and random keep every id they find")
    void keepingSchemesMoveNothing() {
        account("Notch", offline("Notch"), AccountKind.PREMIUM, MOJANG);

        assertThat(change().change(UuidScheme.KEEP_FIRST)).isEqualTo(new ChangeScheme.Changed.Done(0, 1, List.of()));
        assertThat(change().change(UuidScheme.RANDOM)).isEqualTo(new ChangeScheme.Changed.Done(0, 1, List.of()));
        assertThat(idOf("Notch")).isEqualTo(offline("Notch"));
    }

    @Test
    @DisplayName("a run that fails halfway leaves the old scheme recorded, so the next run picks up where it stopped")
    void aFailureRecordsNothing() {
        account("Notch", offline("Notch"), AccountKind.PREMIUM, MOJANG);
        failing = true;

        assertThatThrownBy(() -> change().change(UuidScheme.MOJANG)).hasMessageContaining("went away");
        assertThat(recorded).isEqualTo(UuidScheme.OFFLINE);
    }

    @Test
    @DisplayName("a player online refuses the change, and nothing moves")
    void aPlayerOnlineRefuses() {
        account("Notch", offline("Notch"), AccountKind.PREMIUM, MOJANG);
        online = 1;

        assertThat(change().change(UuidScheme.MOJANG)).isEqualTo(new ChangeScheme.Changed.PlayersOnline());
        assertThat(done).isEmpty();
    }
}
