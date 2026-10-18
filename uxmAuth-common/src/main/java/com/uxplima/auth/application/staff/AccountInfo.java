package com.uxplima.auth.application.staff;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.name.PlayerName;

/**
 * What staff read of one account: who it is, how it logs in, and which other accounts share its addresses. Every
 * read goes to the tables, so it runs off the server thread.
 */
public final class AccountInfo {

    /** How many of the latest logins, and how many other accounts, a view shows. */
    public record Settings(int recentLogins, int alts) {
        public Settings {
            if (recentLogins < 1 || alts < 1) {
                throw new IllegalArgumentException("a view shows at least one login and one other account");
            }
        }
    }

    /**
     * One account as staff read it.
     *
     * @param address the confirmed mail address, masked
     * @param alts the other accounts seen on the addresses this one used
     */
    public record View(
            Account account,
            int logins,
            List<LoginHistory.Entry> recent,
            boolean app,
            int backupCodes,
            boolean mailedCode,
            Optional<String> address,
            List<PlayerName> alts) {
        public View {
            Objects.requireNonNull(account, "account");
            recent = List.copyOf(recent);
            Objects.requireNonNull(address, "address");
            alts = List.copyOf(alts);
        }
    }

    private final AccountRepository accounts;
    private final LoginHistory history;
    private final Factors factors;
    private final Emails emails;
    private final Supplier<Settings> settings;

    public AccountInfo(
            AccountRepository accounts,
            LoginHistory history,
            Factors factors,
            Emails emails,
            Supplier<Settings> settings) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.history = Objects.requireNonNull(history, "history");
        this.factors = Objects.requireNonNull(factors, "factors");
        this.emails = Objects.requireNonNull(emails, "emails");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** The account under this name, or nobody. */
    public Optional<View> of(PlayerName name) {
        return accounts.findByName(Objects.requireNonNull(name, "name")).map(this::view);
    }

    /** The latest logins of the account under this name, newest first, or nobody. */
    public Optional<List<LoginHistory.Entry>> history(PlayerName name, int limit) {
        return accounts.findByName(Objects.requireNonNull(name, "name"))
                .map(account -> history.recent(account.id(), limit));
    }

    private View view(Account account) {
        Settings shown = settings.get();
        UUID id = account.id();
        List<LoginHistory.Entry> recent = history.recent(id, shown.recentLogins());
        return new View(
                account,
                history.count(id),
                recent,
                factors.app(id).isPresent(),
                factors.backupCodes(id).size(),
                emails.mailsLoginCode(id),
                emails.address(id).map(EmailAddress::masked),
                alts(account, recent, shown.alts()));
    }

    /** The other accounts on the address it registered from, its last one and those of its latest logins. */
    private List<PlayerName> alts(Account account, List<LoginHistory.Entry> recent, int cap) {
        Set<String> addresses = new LinkedHashSet<>();
        addresses.add(account.registrationAddress());
        if (account.lastAddress() != null) {
            addresses.add(account.lastAddress());
        }
        recent.forEach(entry -> addresses.add(entry.address()));
        addresses.remove("");
        Set<UUID> others = new LinkedHashSet<>();
        for (String address : addresses) {
            for (UUID other : history.accountsOn(address, cap + 1)) {
                if (!other.equals(account.id()) && others.size() < cap) {
                    others.add(other);
                }
            }
        }
        List<PlayerName> names = new ArrayList<>();
        others.forEach(other -> accounts.findById(other).map(Account::name).ifPresent(names::add));
        return names;
    }
}
