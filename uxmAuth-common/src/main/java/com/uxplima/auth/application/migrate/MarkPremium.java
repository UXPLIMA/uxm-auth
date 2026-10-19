package com.uxplima.auth.application.migrate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.ImportMarks;
import com.uxplima.auth.application.port.ImportSource;
import com.uxplima.auth.application.port.ImportedAccount;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.NameVerdict;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.UuidScheme;

/**
 * Reads another plugin's list of premium names, once, after the accounts came over, and turns each cracked account
 * it names premium. It creates no account: a list of names holds no password to log in with.
 *
 * <p>A premium id the list gives two names is a player who changed their name; the web is asked which name holds it
 * now, and only that one is turned. No answer leaves both as they were.
 */
public final class MarkPremium {

    private final AccountRepository accounts;
    private final ImportMarks marks;
    private final Supplier<NamePolicy> names;
    private final Function<PlayerName, PremiumAnswer> lookup;
    private final UuidScheme scheme;
    private final Clock clock;

    /** @param lookup asks the operator's sources who holds a name; it may go to the web */
    public MarkPremium(
            AccountRepository accounts,
            ImportMarks marks,
            Supplier<NamePolicy> names,
            Function<PlayerName, PremiumAnswer> lookup,
            UuidScheme scheme,
            Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.marks = Objects.requireNonNull(marks, "marks");
        this.names = Objects.requireNonNull(names, "names");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Reads the list unless it was read before, and says what it did. A failure marks nothing. */
    public PremiumReport run(ImportSource source) {
        String key = Objects.requireNonNull(source, "source").key();
        if (marks.completed(key).isPresent()) {
            return PremiumReport.done(key);
        }
        Tally tally = new Tally();
        Map<UUID, List<PlayerName>> byId = new LinkedHashMap<>();
        try (Stream<ImportedAccount> rows = source.read()) {
            rows.forEach(row -> collect(row, byId, tally));
        }
        byId.forEach((id, holders) -> owner(id, holders, tally).ifPresent(name -> turn(name, id, tally)));
        marks.complete(key, clock.instant());
        return tally.report(key);
    }

    private void collect(ImportedAccount row, Map<UUID, List<PlayerName>> byId, Tally tally) {
        UUID premiumId = row.premiumId();
        if (premiumId == null) {
            return;
        }
        if (!(names.get().check(row.name()) instanceof NameVerdict.Accepted accepted)) {
            tally.refusedNames++;
            return;
        }
        List<PlayerName> holders = byId.computeIfAbsent(premiumId, unused -> new ArrayList<>());
        if (holders.stream().noneMatch(held -> held.key().equals(accepted.name().key()))) {
            holders.add(accepted.name());
        }
    }

    /** The one name that holds the id: the only one listed, or the one the web gives it. */
    private Optional<PlayerName> owner(UUID id, List<PlayerName> holders, Tally tally) {
        if (holders.size() == 1) {
            return Optional.of(holders.getFirst());
        }
        int unknown = 0;
        for (PlayerName name : holders) {
            PremiumAnswer answer = lookup.apply(name);
            if (answer instanceof PremiumAnswer.Premium paid && paid.mojangId().equals(id)) {
                tally.notOwner += holders.size() - 1 - unknown;
                tally.unanswered += unknown;
                return Optional.of(name);
            }
            unknown += answer instanceof PremiumAnswer.Unknown ? 1 : 0;
        }
        tally.unanswered += unknown;
        tally.notOwner += holders.size() - unknown;
        return Optional.empty();
    }

    private void turn(PlayerName name, UUID premiumId, Tally tally) {
        Optional<Account> found = accounts.findByName(name);
        if (found.isEmpty()) {
            tally.unknownNames++;
            return;
        }
        Account account = found.get();
        if (account.kind() != AccountKind.CRACKED) {
            tally.already++;
            return;
        }
        if (accounts.findByPremiumId(premiumId).isPresent()) {
            tally.taken++;
            return;
        }
        UUID id = scheme.idFor(account.name(), Optional.of(premiumId), Optional.of(account));
        if (!id.equals(account.id())) {
            accounts.moveId(account.id(), id);
        }
        accounts.update(new Account(
                id,
                account.name(),
                AccountKind.PREMIUM,
                premiumId,
                account.password(),
                account.registeredAt(),
                account.registrationAddress(),
                account.lastLoginAt(),
                account.lastAddress()));
        tally.marked++;
    }

    /** The counts of one run, kept on the one thread that runs it. */
    private static final class Tally {
        int marked;
        int already;
        int unknownNames;
        int taken;
        int notOwner;
        int unanswered;
        int refusedNames;

        PremiumReport report(String key) {
            return new PremiumReport(
                    key, false, marked, already, unknownNames, taken, notOwner, unanswered, refusedNames);
        }
    }
}
