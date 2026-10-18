package com.uxplima.auth.application.migrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import com.uxplima.auth.domain.hash.Argon2idHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.LegacyDigestHasher;
import com.uxplima.auth.domain.hash.LegacyHashes;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.testing.InMemoryAccounts;
import com.uxplima.auth.testing.InMemoryFactors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Accounts another login plugin kept come across once, whole, and a second run updates what the first wrote. */
final class ImportAccountsTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    /** "hunter22", hashed as SHA-256 with no salt, from Python's hashlib. */
    private static final String PLAIN_256 = "20d2fe5e369db54ec7090639a9dc30ec4d608604936239d39e2de07fda09eb0b";

    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final InMemoryFactors factors = new InMemoryFactors();
    private final Map<String, Instant> marks = new ConcurrentHashMap<>();
    private final Hashing hashing = new Hashing(new Argon2idHasher(1024, 1, 1), List.of(new LegacyDigestHasher()));

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

    private ImportAccounts importer() {
        return new ImportAccounts(
                accounts,
                factors,
                marked,
                () -> new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of()),
                UuidScheme.OFFLINE,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** A source that hands these rows over, and fails at the row {@code failAt} when it is not -1. */
    private static ImportSource source(List<ImportedAccount> rows, int failAt) {
        return new ImportSource() {
            @Override
            public String key() {
                return "test-source";
            }

            @Override
            public Stream<ImportedAccount> read() {
                List<ImportedAccount> handed = new ArrayList<>();
                return rows.stream().peek(row -> {
                    if (handed.size() == failAt) {
                        throw new IllegalStateException("the source went away");
                    }
                    handed.add(row);
                });
            }

            @Override
            public void close() {}
        };
    }

    private static LegacyHashes.Read known(String raw) {
        return LegacyHashes.rewrite(LegacyHashes.Style.PLAIN, raw, null, null);
    }

    private static ImportedAccount cracked(String name, @Nullable UUID id, @Nullable Instant lastLogin) {
        return new ImportedAccount(
                name, id, null, known(PLAIN_256), null, NOW.minusSeconds(86_400), lastLogin, "203.0.113.7");
    }

    private Account stored(String name) {
        return accounts.findByName(new PlayerName(name)).orElseThrow();
    }

    @Test
    @DisplayName("a cracked, a premium and a second-factor account come across with their ids, hashes and dates")
    void accountsComeAcross() {
        UUID kept = UUID.randomUUID();
        UUID mojang = UUID.randomUUID();
        ImportReport report = importer()
                .run(source(
                        List.of(
                                cracked("Steve", kept, NOW.minusSeconds(60)),
                                new ImportedAccount("Notch", mojang, mojang, null, null, null, null, null),
                                new ImportedAccount("Alex", null, null, known(PLAIN_256), SECRET, null, null, null)),
                        -1));

        assertThat(report.imported()).isEqualTo(3);
        assertThat(stored("Steve").id())
                .as("the source's id, which other plugins keep data by")
                .isEqualTo(kept);
        assertThat(hashing.verify(
                        Password.of("hunter22"),
                        java.util.Objects.requireNonNull(stored("Steve").password())))
                .isInstanceOf(Verification.RightButRehash.class);
        assertThat(stored("Steve").registeredAt()).isEqualTo(NOW.minusSeconds(86_400));
        assertThat(stored("Steve").lastLoginAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(stored("Notch").kind()).isEqualTo(AccountKind.PREMIUM);
        assertThat(stored("Notch").premiumId()).isEqualTo(mojang);
        assertThat(stored("Alex").id())
                .as("no id given: the recorded scheme's")
                .isEqualTo(UuidScheme.offline(new PlayerName("Alex")));
        assertThat(factors.app(stored("Alex").id())).isPresent();
        assertThat(marks).containsKey("test-source");
    }

    @Test
    @DisplayName("a name the server refuses is counted and named, and the import still completes")
    void aRefusedNameIsCounted() {
        ImportReport report =
                importer().run(source(List.of(cracked("has space", null, null), cracked("Steve", null, null)), -1));

        assertThat(report.imported()).isEqualTo(1);
        assertThat(report.refusedNames()).containsExactly("has space");
        assertThat(marks).containsKey("test-source");
    }

    @Test
    @DisplayName("two rows for one name in another case: the one that logged in last wins, the other is counted")
    void theNewerOfACasePairWins() {
        ImportReport report = importer()
                .run(source(List.of(cracked("Steve", null, NOW.minusSeconds(600)), cracked("STEVE", null, NOW)), -1));

        assertThat(report.imported()).isEqualTo(1);
        assertThat(report.duplicates()).isEqualTo(1);
        assertThat(stored("steve").name().value()).isEqualTo("STEVE");
    }

    @Test
    @DisplayName("a hash nobody reads keeps its account, opens nothing, and is counted by its format")
    void anUnknownHashIsCounted() {
        LegacyHashes.Read unknown = LegacyHashes.rewrite(LegacyHashes.Style.AS_IS, "$md5crypt$abc$def", null, null);
        ImportReport report = importer()
                .run(source(List.of(new ImportedAccount("Steve", null, null, unknown, null, null, null, null)), -1));

        assertThat(report.unknownHashes()).containsEntry("$md5crypt$", 1);
        assertThat(hashing.verify(
                        Password.of("hunter22"),
                        java.util.Objects.requireNonNull(stored("Steve").password())))
                .isInstanceOf(Verification.Wrong.class);
    }

    @Test
    @DisplayName("a secret that is not Base32 is counted, and its account still comes across")
    void aBadSecretIsCounted() {
        ImportReport report = importer()
                .run(source(
                        List.of(new ImportedAccount(
                                "Steve", null, null, known(PLAIN_256), "not base32!", null, null, null)),
                        -1));

        assertThat(report.imported()).isEqualTo(1);
        assertThat(report.refusedSecrets()).isEqualTo(1);
        assertThat(factors.app(stored("Steve").id())).isEmpty();
    }

    @Test
    @DisplayName("a source that fails halfway marks nothing, and the next run updates what was written, twice nothing")
    void anInterruptedImportReruns() {
        List<ImportedAccount> rows =
                List.of(cracked("Steve", null, null), cracked("Alex", null, null), cracked("Herobrine", null, null));

        assertThatThrownBy(() -> importer().run(source(rows, 2))).hasMessageContaining("went away");
        assertThat(marks).isEmpty();

        ImportReport rerun = importer().run(source(rows, -1));
        assertThat(rerun.imported() + rerun.updated()).isEqualTo(3);
        assertThat(accounts.findByName(new PlayerName("Herobrine"))).isPresent();
        assertThat(marks).containsKey("test-source");

        ImportReport third = importer().run(source(rows, -1));
        assertThat(third.alreadyDone()).isTrue();
        assertThat(third.imported() + third.updated()).isZero();
    }

    @Test
    @DisplayName("an account the plugin already holds is updated under its own id, never doubled")
    void anExistingAccountIsUpdated() {
        UUID own = UUID.randomUUID();
        accounts.insert(new Account(
                own,
                new PlayerName("Steve"),
                AccountKind.CRACKED,
                null,
                new PasswordHash("$2a$04$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234"),
                NOW,
                "a",
                null,
                null));

        ImportReport report = importer().run(source(List.of(cracked("Steve", UUID.randomUUID(), null)), -1));

        assertThat(report.updated()).isEqualTo(1);
        assertThat(stored("Steve").id()).isEqualTo(own);
        assertThat(hashing.verify(
                        Password.of("hunter22"),
                        java.util.Objects.requireNonNull(stored("Steve").password())))
                .isInstanceOf(Verification.RightButRehash.class);
    }
}
