package com.uxplima.auth.application.migrate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.application.port.ImportMarks;
import com.uxplima.auth.application.port.ImportSource;
import com.uxplima.auth.application.port.ImportedAccount;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.LegacyHashes;
import com.uxplima.auth.domain.hash.LegacyHashes.Read;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.NameVerdict;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.UuidScheme;
import org.jspecify.annotations.Nullable;

/**
 * Brings another login plugin's accounts over, once.
 *
 * <p>The whole source is read before anything is written, so a source that fails halfway leaves nothing behind
 * and marks nothing complete; the next start reads it again. Every account is written by its name, so a second run
 * updates what the first wrote and never doubles it. An account the plugin held already keeps its own id.
 */
public final class ImportAccounts {

    private final AccountRepository accounts;
    private final Factors factors;
    private final ImportMarks marks;
    private final Supplier<NamePolicy> names;
    private final UuidScheme scheme;
    private final Clock clock;

    public ImportAccounts(
            AccountRepository accounts,
            Factors factors,
            ImportMarks marks,
            Supplier<NamePolicy> names,
            UuidScheme scheme,
            Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.factors = Objects.requireNonNull(factors, "factors");
        this.marks = Objects.requireNonNull(marks, "marks");
        this.names = Objects.requireNonNull(names, "names");
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Imports the source unless it was imported before, and says what it did. A failure marks nothing. */
    public ImportReport run(ImportSource source) {
        String key = Objects.requireNonNull(source, "source").key();
        if (marks.completed(key).isPresent()) {
            return ImportReport.done(key);
        }
        Tally tally = new Tally();
        Map<String, Picked> picked = new LinkedHashMap<>();
        try (Stream<ImportedAccount> rows = source.read()) {
            rows.forEach(row -> pick(row, picked, tally));
        }
        picked.values().forEach(one -> write(one, tally));
        marks.complete(key, clock.instant());
        return tally.report(key);
    }

    /** An account whose name this server accepts, with the row it came from. */
    private record Picked(PlayerName name, ImportedAccount row) {
        Instant seen() {
            return Objects.requireNonNullElse(row.lastLogin(), Instant.EPOCH);
        }
    }

    private void pick(ImportedAccount row, Map<String, Picked> picked, Tally tally) {
        if (!(names.get().check(row.name()) instanceof NameVerdict.Accepted accepted)) {
            tally.refused(row.name());
            return;
        }
        Picked one = new Picked(accepted.name(), row);
        Picked before = picked.putIfAbsent(accepted.name().key(), one);
        if (before != null) {
            tally.duplicates++;
            // Two rows for one name in another case: the one that played last is the player's.
            if (one.seen().isAfter(before.seen())) {
                picked.put(accepted.name().key(), one);
            }
        }
    }

    private void write(Picked one, Tally tally) {
        Optional<Account> existing = accounts.findByName(one.name());
        Account account = accountFor(one, existing);
        if (existing.isPresent()) {
            accounts.update(account);
            tally.updated++;
        } else if (accounts.insert(account) instanceof AccountRepository.Inserted) {
            tally.imported++;
        } else {
            tally.duplicates++;
            return;
        }
        if (one.row().password() instanceof LegacyHashes.Unknown unknown) {
            tally.unknownHashes.merge(unknown.format(), 1, Integer::sum);
        }
        keepSecret(account.id(), one.row().totpSecret(), tally);
    }

    private Account accountFor(Picked one, Optional<Account> existing) {
        ImportedAccount row = one.row();
        UUID id = existing.map(Account::id)
                .or(() -> Optional.ofNullable(row.id()))
                .orElseGet(() -> scheme.idFor(one.name(), Optional.ofNullable(row.premiumId()), Optional.empty()));
        Instant registered = Optional.ofNullable(row.registeredAt())
                .or(() -> existing.map(Account::registeredAt))
                .orElseGet(clock::instant);
        String registeredFrom = existing.map(Account::registrationAddress)
                .orElseGet(() -> Objects.requireNonNullElse(row.lastAddress(), ""));
        return new Account(
                id,
                one.name(),
                row.premiumId() == null ? AccountKind.CRACKED : AccountKind.PREMIUM,
                row.premiumId(),
                hashOf(row.password()),
                registered,
                registeredFrom,
                row.lastLogin(),
                row.lastAddress());
    }

    private static @Nullable PasswordHash hashOf(@Nullable Read read) {
        return switch (read) {
            case null -> null;
            case LegacyHashes.Known known -> known.hash();
            case LegacyHashes.Unknown unknown -> unknown.kept();
        };
    }

    private void keepSecret(UUID account, @Nullable String secret, Tally tally) {
        if (secret == null || secret.isBlank()) {
            return;
        }
        try {
            factors.setApp(account, TotpSecret.fromBase32(secret.trim()));
        } catch (IllegalArgumentException notASecret) {
            tally.refusedSecrets++;
        }
    }

    /** The counts of one run, kept on the one thread that runs it. */
    private static final class Tally {
        int imported;
        int updated;
        int duplicates;
        int refusedSecrets;
        int refusedNameCount;
        final List<String> refusedNames = new ArrayList<>();
        final Map<String, Integer> unknownHashes = new LinkedHashMap<>();

        void refused(String name) {
            refusedNameCount++;
            if (refusedNames.size() < ImportReport.NAMED) {
                refusedNames.add(name);
            }
        }

        ImportReport report(String key) {
            return new ImportReport(
                    key,
                    false,
                    imported,
                    updated,
                    duplicates,
                    refusedSecrets,
                    refusedNames,
                    refusedNameCount,
                    unknownHashes);
        }
    }
}
