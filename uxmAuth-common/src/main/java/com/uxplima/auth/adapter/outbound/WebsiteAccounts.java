package com.uxplima.auth.adapter.outbound;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.uxmlib.storage.sql.Database;
import org.jspecify.annotations.Nullable;

/**
 * The plugin's accounts with a website's login table beside them, so one password opens both.
 *
 * <p>The website's row holds the password: one changed on the website is the one a login reads, and one changed
 * in game is written to the website in its own format. An account the website made gets the plugin's row the first
 * time its name is looked up. Everything else is the plugin's alone, and the plugin's table is still written, so
 * turning the website table off loses nothing but what the website changed since.
 */
public final class WebsiteAccounts implements AccountRepository {

    private final AccountRepository accounts;
    private final WebsiteRows rows;
    private final Clock clock;

    private WebsiteAccounts(AccountRepository accounts, WebsiteRows rows, Clock clock) {
        this.accounts = accounts;
        this.rows = rows;
        this.clock = clock;
    }

    /** The plugin's accounts, with the website's table beside them while it is on. */
    public static AccountRepository beside(
            AccountRepository accounts, Database database, WebsiteTable website, Clock clock) {
        return Objects.requireNonNull(website, "website").enabled()
                ? over(accounts, database, website, clock)
                : accounts;
    }

    /** The accounts with the website's table beside them, the table made first if it is missing. */
    public static WebsiteAccounts over(
            AccountRepository accounts, Database database, WebsiteTable website, Clock clock) {
        WebsiteRows rows = new WebsiteRows(database, website);
        rows.prepare();
        return new WebsiteAccounts(
                Objects.requireNonNull(accounts, "accounts"), rows, Objects.requireNonNull(clock, "clock"));
    }

    @Override
    public Optional<Account> findByName(PlayerName name) {
        Optional<WebsiteRows.Row> row = rows.find(Objects.requireNonNull(name, "name"));
        Optional<Account> found = accounts.findByName(name);
        if (found.isPresent()) {
            return found.map(account -> withTheWebsites(account, row));
        }
        return row.flatMap(this::adopted);
    }

    /** The plugin's row for an account the website made, written now. */
    private Optional<Account> adopted(WebsiteRows.Row row) {
        Optional<PlayerName> name = nameOf(row);
        if (name.isEmpty()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Account made = new Account(
                UuidScheme.offline(name.get()),
                name.get(),
                AccountKind.CRACKED,
                null,
                new PasswordHash(row.password()),
                row.registeredAt() > 0 ? Instant.ofEpochMilli(row.registeredAt()) : now,
                Objects.requireNonNullElse(row.registrationAddress(), ""),
                row.lastLogin() > 0 ? Instant.ofEpochMilli(row.lastLogin()) : null,
                row.lastAddress());
        return switch (accounts.insert(made)) {
            case Inserted inserted -> Optional.of(made);
            case NameTaken taken -> accounts.findByName(taken.stored());
        };
    }

    /** The name as the website wrote it, or its key when that is not a name this plugin can hold. */
    private static Optional<PlayerName> nameOf(WebsiteRows.Row row) {
        for (String written : new String[] {row.realName(), row.name()}) {
            if (written != null && written.equalsIgnoreCase(row.name())) {
                try {
                    return Optional.of(new PlayerName(written));
                } catch (IllegalArgumentException notAName) {
                    // The key is tried next, and a row neither names is left to the website.
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the website keeps this account. Only a password account is the website's: a premium or Bedrock one is
     * proved elsewhere, so a website row under its name is somebody else's and gives it nothing.
     */
    private static boolean mirrored(Account account) {
        return account.kind() == AccountKind.CRACKED && account.password() != null;
    }

    private static Account withTheWebsites(Account account, Optional<WebsiteRows.Row> row) {
        if (!mirrored(account)) {
            return account;
        }
        return row.map(found -> account.withPassword(new PasswordHash(found.password())))
                .orElse(account);
    }

    private Optional<Account> withTheWebsites(Optional<Account> account) {
        return account.map(found -> mirrored(found) ? withTheWebsites(found, rows.find(found.name())) : found);
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return withTheWebsites(accounts.findById(Objects.requireNonNull(id, "id")));
    }

    @Override
    public Optional<Account> findByPremiumId(UUID premiumId) {
        return withTheWebsites(accounts.findByPremiumId(Objects.requireNonNull(premiumId, "premiumId")));
    }

    /**
     * Stores a new account. A name the website holds is taken, even when it took it a moment ago: the plugin's row is
     * taken back, and the website's row is never written over by a registration.
     */
    @Override
    public Insert insert(Account account) {
        Insert result = accounts.insert(Objects.requireNonNull(account, "account"));
        if (result instanceof Inserted && !added(account)) {
            accounts.delete(account.id());
            return new NameTaken(
                    rows.find(account.name()).flatMap(WebsiteAccounts::nameOf).orElse(account.name()));
        }
        return result;
    }

    /** Gives a password account its website row. False when the website took the name first. */
    private boolean added(Account account) {
        PasswordHash password = account.password();
        return password == null || !mirrored(account) || rows.add(account, password);
    }

    /** Writes every field, for staff tools; the website's row is this account's, since the plugin holds its name. */
    @Override
    public void update(Account account) {
        accounts.update(Objects.requireNonNull(account, "account"));
        PasswordHash password = account.password();
        if (password != null && mirrored(account)) {
            rows.write(account, password);
        }
    }

    @Override
    public void recordLogin(UUID id, Instant at, String address) {
        accounts.recordLogin(id, at, address);
        accounts.findById(id).filter(WebsiteAccounts::mirrored).ifPresent(account -> {
            // An account the plugin held before the table was turned on reaches the website here.
            if (rows.find(account.name()).isEmpty()) {
                added(account);
            }
            rows.recordLogin(account.name(), at, address);
        });
    }

    @Override
    public boolean replacePassword(UUID id, @Nullable PasswordHash expected, PasswordHash fresh) {
        Objects.requireNonNull(fresh, "fresh");
        Optional<Account> account =
                accounts.findById(Objects.requireNonNull(id, "id")).filter(WebsiteAccounts::mirrored);
        Optional<WebsiteRows.Row> row = account.flatMap(found -> rows.find(found.name()));
        if (account.isEmpty() || row.isEmpty()) {
            boolean replaced = accounts.replacePassword(id, expected, fresh);
            if (replaced) {
                account.ifPresent(found -> added(found.withPassword(fresh)));
            }
            return replaced;
        }
        if (expected == null || !rows.replace(account.get().name(), expected.encoded(), fresh)) {
            return false;
        }
        accounts.update(account.get().withPassword(fresh));
        return true;
    }

    @Override
    public int countRegisteredFrom(String address, Instant since) {
        return accounts.countRegisteredFrom(address, since);
    }

    @Override
    public Insert rename(UUID id, PlayerName to, Instant at) {
        Optional<Account> before =
                accounts.findById(Objects.requireNonNull(id, "id")).filter(WebsiteAccounts::mirrored);
        Optional<WebsiteRows.Row> theirs =
                before.isPresent() ? rows.find(Objects.requireNonNull(to, "to")) : Optional.empty();
        if (theirs.isPresent()
                && !theirs.get().name().equalsIgnoreCase(before.get().name().value())) {
            return new NameTaken(nameOf(theirs.get()).orElse(to));
        }
        Insert result = accounts.rename(id, to, at);
        if (result instanceof Inserted) {
            before.ifPresent(account -> rows.rename(account.name(), to));
        }
        return result;
    }

    @Override
    public Optional<PlayerName> renamedFrom(PlayerName old) {
        return accounts.renamedFrom(old);
    }

    @Override
    public Optional<Renamed> untoldRename(UUID id) {
        return accounts.untoldRename(id);
    }

    @Override
    public void markRenameTold(UUID id) {
        accounts.markRenameTold(id);
    }

    @Override
    public void delete(UUID id) {
        Optional<Account> gone = accounts.findById(Objects.requireNonNull(id, "id"));
        accounts.delete(id);
        gone.filter(WebsiteAccounts::mirrored).ifPresent(account -> rows.delete(account.name()));
    }

    @Override
    public void moveId(UUID from, UUID to) {
        accounts.moveId(from, to);
    }
}
