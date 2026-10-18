package com.uxplima.auth.application.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.testing.AuthFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Staff turn an account premium or cracked, through the conversion the door uses. */
final class MarkKindTest {

    private static final UUID MOJANG = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private final AuthFixture fixture = new AuthFixture(1);
    private final List<Bulletin> published = new ArrayList<>();
    private final Map<String, PremiumAnswer> web = new HashMap<>();
    private final NetworkEvents events = new NetworkEvents() {
        @Override
        public void publish(Bulletin bulletin) {
            published.add(bulletin);
        }

        @Override
        public void listen(Consumer<Bulletin> listener) {}
    };

    @AfterEach
    void close() {
        fixture.close();
    }

    private MarkKind mark(UuidScheme scheme) {
        return new MarkKind(
                fixture.accounts, name -> web.getOrDefault(name.key(), new PremiumAnswer.Unknown()), scheme, events);
    }

    private Account steve() {
        fixture.registered("Steve", "hunter22");
        return fixture.accounts.findByName(new PlayerName("Steve")).orElseThrow();
    }

    @Test
    @DisplayName("a paid name turns premium under its Mojang id, keeps its password, and its player rejoins")
    void premiumUnderMojang() {
        Account before = steve();
        web.put("steve", new PremiumAnswer.Premium(MOJANG, "Steve"));

        assertThat(mark(UuidScheme.MOJANG).premium(new PlayerName("steve"))).isEqualTo(MarkKind.Marked.DONE);

        Account after = fixture.accounts.findByName(new PlayerName("Steve")).orElseThrow();
        assertThat(after.kind()).isEqualTo(AccountKind.PREMIUM);
        assertThat(after.premiumId()).isEqualTo(MOJANG);
        assertThat(after.id()).isEqualTo(MOJANG);
        assertThat(after.password()).isEqualTo(before.password());
        assertThat(fixture.accounts.findById(before.id()))
                .as("the old id holds nothing now")
                .isEmpty();
        assertThat(published).containsExactly(new Bulletin.Rejoin(before.id()));
    }

    @Test
    @DisplayName("under keep-first the account keeps its id")
    void premiumKeepsTheId() {
        Account before = steve();
        web.put("steve", new PremiumAnswer.Premium(MOJANG, "Steve"));

        mark(UuidScheme.KEEP_FIRST).premium(new PlayerName("Steve"));

        assertThat(fixture.accounts
                        .findByName(new PlayerName("Steve"))
                        .orElseThrow()
                        .id())
                .isEqualTo(before.id());
    }

    @Test
    @DisplayName("a name no paid account holds, a source that cannot say, and an account already premium change"
            + " nothing")
    void premiumRefusals() {
        steve();
        MarkKind marks = mark(UuidScheme.MOJANG);

        assertThat(marks.premium(new PlayerName("Steve"))).isEqualTo(MarkKind.Marked.NO_ANSWER);
        web.put("steve", new PremiumAnswer.Cracked());
        assertThat(marks.premium(new PlayerName("Steve"))).isEqualTo(MarkKind.Marked.NOT_PAID);
        web.put("steve", new PremiumAnswer.Premium(MOJANG, "Steve"));
        marks.premium(new PlayerName("Steve"));
        assertThat(marks.premium(new PlayerName("Steve"))).isEqualTo(MarkKind.Marked.ALREADY);
        assertThat(marks.premium(new PlayerName("Nobody"))).isEqualTo(MarkKind.Marked.UNKNOWN);
        assertThat(published).hasSize(1);
    }

    @Test
    @DisplayName("a Mojang id another account holds already is refused")
    void anotherHoldsTheMojangId() {
        steve();
        fixture.registered("Alex", "hunter22");
        web.put("steve", new PremiumAnswer.Premium(MOJANG, "Steve"));
        web.put("alex", new PremiumAnswer.Premium(MOJANG, "Alex"));
        MarkKind marks = mark(UuidScheme.KEEP_FIRST);
        marks.premium(new PlayerName("Steve"));

        assertThat(marks.premium(new PlayerName("Alex"))).isEqualTo(MarkKind.Marked.TAKEN);
        assertThat(fixture.accounts
                        .findByName(new PlayerName("Alex"))
                        .orElseThrow()
                        .kind())
                .isEqualTo(AccountKind.CRACKED);
    }

    @Test
    @DisplayName("a premium account with a password turns cracked; one with none, or one cracked already, does not")
    void cracked() {
        Account before = steve();
        web.put("steve", new PremiumAnswer.Premium(MOJANG, "Steve"));
        MarkKind marks = mark(UuidScheme.KEEP_FIRST);
        marks.premium(new PlayerName("Steve"));
        published.clear();

        assertThat(marks.cracked(new PlayerName("Steve"))).isEqualTo(MarkKind.Marked.DONE);
        Account after = fixture.accounts.findByName(new PlayerName("Steve")).orElseThrow();
        assertThat(after.kind()).isEqualTo(AccountKind.CRACKED);
        assertThat(after.premiumId()).isNull();
        assertThat(published).containsExactly(new Bulletin.Rejoin(before.id()));
        assertThat(marks.cracked(new PlayerName("Steve"))).isEqualTo(MarkKind.Marked.ALREADY);

        fixture.accounts.seed(new Account(
                UUID.randomUUID(),
                new PlayerName("Notch"),
                AccountKind.PREMIUM,
                MOJANG,
                null,
                AuthFixture.NOW,
                "10.0.0.1",
                null,
                null));
        assertThat(marks.cracked(new PlayerName("Notch"))).isEqualTo(MarkKind.Marked.NO_PASSWORD);
    }

    @Test
    @DisplayName("on the real table the account moves to its Mojang id, with everything under it")
    void theTableMovesTheId(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) {
        com.uxplima.uxmlib.storage.sql.Database database =
                com.uxplima.auth.adapter.outbound.AuthSchema.migrate(com.uxplima.uxmlib.storage.sql.Database.builder()
                        .sqlite(folder.resolve("auth.db"))
                        .poolName("uxmauth-mark")
                        .build());
        try {
            com.uxplima.auth.adapter.outbound.SqlAccountRepository table =
                    new com.uxplima.auth.adapter.outbound.SqlAccountRepository(database);
            Account steve = AuthFixture.account("Steve", new com.uxplima.auth.domain.hash.PasswordHash("$x$a"));
            table.insert(steve);
            web.put("steve", new PremiumAnswer.Premium(MOJANG, "Steve"));

            new MarkKind(table, name -> web.get(name.key()), UuidScheme.MOJANG, events)
                    .premium(new PlayerName("Steve"));

            assertThat(table.findById(steve.id())).isEmpty();
            assertThat(table.findById(MOJANG))
                    .hasValueSatisfying(found -> assertThat(found.kind()).isEqualTo(AccountKind.PREMIUM));
        } finally {
            database.close();
        }
    }

    @Test
    @DisplayName("a kind staff switched is told to the listeners once each way; a refused switch tells nothing")
    void aSwitchIsTold() {
        steve();
        web.put("steve", new PremiumAnswer.Premium(MOJANG, "Steve"));
        com.uxplima.auth.testing.HeardEvents listeners = new com.uxplima.auth.testing.HeardEvents();
        MarkKind told = new MarkKind(
                fixture.accounts,
                name -> web.getOrDefault(name.key(), new PremiumAnswer.Unknown()),
                UuidScheme.KEEP_FIRST,
                events,
                listeners);

        assertThat(told.cracked(new PlayerName("Steve"))).isEqualTo(MarkKind.Marked.ALREADY);
        assertThat(told.premium(new PlayerName("Steve"))).isEqualTo(MarkKind.Marked.DONE);
        assertThat(told.cracked(new PlayerName("Steve"))).isEqualTo(MarkKind.Marked.DONE);

        assertThat(listeners.heard).containsExactly("switched Steve PREMIUM", "switched Steve CRACKED");
    }
}
