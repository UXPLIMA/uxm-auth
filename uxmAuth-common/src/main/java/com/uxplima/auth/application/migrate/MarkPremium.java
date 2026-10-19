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
 * <p>Every name is asked of the web, because a list can be years old and a name changes hands. A premium id the
 * list gives two names is a player who changed their name; only the name the web gives that id now is turned. No
 * answer leaves the account as it was, and the door asks again when its player joins.
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
        byId.forEach((id, holders) -> mark(id, holders, tally));
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

    /**
     * Turns the one cracked account the web gives the id, and none when it gives it to nobody listed. A flag the
     * list kept is never trusted alone: the name may have changed hands since it was written.
     */
    private void mark(UUID id, List<PlayerName> holders, Tally tally) {
        List<Account> cracked = cracked(holders, tally);
        if (cracked.isEmpty()) {
            return;
        }
        if (accounts.findByPremiumId(id).isPresent()) {
            tally.taken += cracked.size();
            return;
        }
        int unknown = 0;
        for (Account account : cracked) {
            PremiumAnswer answer = lookup.apply(account.name());
            if (answer instanceof PremiumAnswer.Premium paid && paid.mojangId().equals(id)) {
                turn(account, id);
                tally.marked++;
                tally.notOwner += cracked.size() - 1 - unknown;
                tally.unanswered += unknown;
                return;
            }
            unknown += answer instanceof PremiumAnswer.Unknown ? 1 : 0;
        }
        tally.unanswered += unknown;
        tally.notOwner += cracked.size() - unknown;
    }

    /** The cracked accounts under the names, counting the names with no account and those of another kind. */
    private List<Account> cracked(List<PlayerName> holders, Tally tally) {
        List<Account> cracked = new ArrayList<>();
        for (PlayerName name : holders) {
            Optional<Account> found = accounts.findByName(name);
            if (found.isEmpty()) {
                tally.unknownNames++;
            } else if (found.get().kind() != AccountKind.CRACKED) {
                tally.already++;
            } else {
                cracked.add(found.get());
            }
        }
        return cracked;
    }

    private void turn(Account account, UUID premiumId) {
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
