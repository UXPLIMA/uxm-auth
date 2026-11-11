package com.uxplima.auth.adapter.outbound;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.uxmlib.storage.StorageException;
import com.uxplima.uxmlib.storage.sql.Database;
import org.jspecify.annotations.Nullable;

/**
 * The plugin's accounts, with the members of a Blysis website as their passwords, so a member logs in with the same
 * name and password on the website and in game.
 *
 * <p>The member holds the password: one changed on the website is the one a login reads, and one changed in game is
 * written to the member. A member who joins for the first time gets the plugin's row then, and a player who registers
 * in game becomes a member. Everything the website does not know (kind, premium id, second factor, sessions) stays in
 * the plugin's tables. Which member an account is, is kept by the member's id, so a member renamed on the website
 * keeps their account here.
 *
 * <p>Every read of the website that fails throws. A login whose member cannot be read is refused, and a name whose
 * members cannot be read is never taken for a free one.
 */
public final class BlysisAccounts implements AccountRepository, AutoCloseable {

    private final AccountRepository accounts;
    private final Database own;
    private final @Nullable Database site;
    private final BlysisMembers members;
    private final BlysisSettings settings;
    private final Clock clock;

    private BlysisAccounts(
            AccountRepository accounts,
            Database own,
            @Nullable Database site,
            BlysisMembers members,
            BlysisSettings settings,
            Clock clock) {
        this.accounts = accounts;
        this.own = own;
        this.site = site;
        this.members = members;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Opens a pool on the website's database, checks its tables, and puts its members beside the accounts. A database
     * that does not answer, or holds no members' table, stops the start: the plugin then refuses every login.
     *
     * @param warn hears what the operator should fix that does not stop the start
     */
    public static BlysisAccounts open(
            AccountRepository accounts,
            Database own,
            BlysisSettings settings,
            Clock clock,
            String poolName,
            Consumer<String> warn) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(warn, "warn");
        AuthDatabase.load(AuthDatabase.driverOf(settings.jdbcUrl(), "blysis.jdbc-url"));
        Database site;
        try {
            site = Database.builder()
                    .poolName(Objects.requireNonNull(poolName, "poolName") + "-blysis")
                    .jdbcUrl(AuthDatabase.addressOf(settings.jdbcUrl()))
                    .username(settings.username())
                    .password(settings.password())
                    .maxPoolSize(settings.poolSize())
                    .connectionTimeoutMs(Math.max(250, settings.timeout().toMillis()))
                    .build();
        } catch (RuntimeException unreachable) {
            throw new StorageException(
                    "the Blysis database at " + AuthDatabase.shown(settings.jdbcUrl()) + " does not answer: "
                            + unreachable.getMessage(),
                    unreachable);
        }
        try {
            BlysisAccounts opened = new BlysisAccounts(
                    Objects.requireNonNull(accounts, "accounts"),
                    Objects.requireNonNull(own, "own"),
                    site,
                    new BlysisMembers(site),
                    settings,
                    Objects.requireNonNull(clock, "clock"));
            opened.members.check(settings.memberRole()).ifPresent(warn);
            return opened;
        } catch (RuntimeException unusable) {
            site.close();
            throw new StorageException(
                    "the Blysis database at " + AuthDatabase.shown(settings.jdbcUrl()) + " cannot be read: "
                            + unusable.getMessage(),
                    unusable);
        }
    }

    /** The accounts with the members of the website in this database beside them; the pool stays the caller's. */
    static BlysisAccounts over(
            AccountRepository accounts, Database own, Database site, BlysisSettings settings, Clock clock) {
        return new BlysisAccounts(
                Objects.requireNonNull(accounts, "accounts"),
                Objects.requireNonNull(own, "own"),
                null,
                new BlysisMembers(Objects.requireNonNull(site, "site")),
                Objects.requireNonNull(settings, "settings"),
                Objects.requireNonNull(clock, "clock"));
    }

    /**
     * Whether a member keeps this account's password. Only a password account is a member: a premium or Bedrock one
     * is proved elsewhere, and a member under its name is somebody else.
     */
    private static boolean mirrored(Account account) {
        return account.kind() == AccountKind.CRACKED && account.password() != null;
    }

    @Override
    public Optional<Account> findByName(PlayerName name) {
        Optional<Account> found = accounts.findByName(Objects.requireNonNull(name, "name"));
        if (found.isPresent()) {
            return found.map(this::withTheMembers);
        }
        return members.named(name)
                .member()
                .filter(BlysisMembers.Member::hasPassword)
                .flatMap(this::adopted);
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return accounts.findById(Objects.requireNonNull(id, "id")).map(this::withTheMembers);
    }

    @Override
    public Optional<Account> findByPremiumId(UUID premiumId) {
        return accounts.findByPremiumId(Objects.requireNonNull(premiumId, "premiumId"))
                .map(this::withTheMembers);
    }

    /**
     * The plugin's row for a member who never joined, written now, under the offline id of their name. It is
     * unverified, as a registration made while no premium source answered is, so the next connection asks whether a
     * paid player owns the name. A member another account is already is left to it: that account moved to another
     * name, and the member is not given a second one.
     */
    private Optional<Account> adopted(BlysisMembers.Member member) {
        if (accountOf(member.id()).isPresent()) {
            return Optional.empty();
        }
        Optional<PlayerName> name = nameOf(member);
        String password = member.password();
        if (name.isEmpty() || password == null) {
            return Optional.empty();
        }
        Instant created = member.createdAt();
        Account made = new Account(
                        UuidScheme.offline(name.get()),
                        name.get(),
                        AccountKind.CRACKED,
                        null,
                        new PasswordHash(password),
                        created == null ? clock.instant() : created,
                        "",
                        null,
                        null)
                .asUnverified();
        return switch (accounts.insert(made)) {
            case Inserted inserted -> {
                link(made.id(), member.id());
                yield Optional.of(made);
            }
            case NameTaken taken -> accounts.findByName(taken.stored()).map(this::withTheMembers);
        };
    }

    /** The member's username, when it is a name a player can join under. */
    private static Optional<PlayerName> nameOf(BlysisMembers.Member member) {
        try {
            return Optional.of(new PlayerName(member.username()));
        } catch (IllegalArgumentException notAName) {
            return Optional.empty();
        }
    }

    /**
     * The account with its member's password. A member who has none any more leaves the account none, so it logs in
     * nowhere; a closed member leaves the password as it was, and the login is refused by {@link #closed}.
     */
    private Account withTheMembers(Account account) {
        if (!mirrored(account)) {
            return account;
        }
        Optional<BlysisMembers.Member> member = memberOf(account);
        if (member.isEmpty() || member.get().closed()) {
            return account;
        }
        String password = member.get().password();
        if (password == null || password.isEmpty()) {
            return new Account(
                    account.id(),
                    account.name(),
                    account.kind(),
                    account.premiumId(),
                    null,
                    account.registeredAt(),
                    account.registrationAddress(),
                    account.lastLoginAt(),
                    account.lastAddress(),
                    account.unverified());
        }
        return account.withPassword(new PasswordHash(password));
    }

    /**
     * The account's member. Once linked, the member with that id, whatever their name is now. Before, the member
     * under the account's name is the account's only when it holds the account's own hash, as one copied from this
     * plugin does: a member somebody registered on the website under the name of an account the plugin held first
     * is theirs, and opens nothing here.
     */
    private Optional<BlysisMembers.Member> memberOf(Account account) {
        Optional<String> linked = linkOf(account.id());
        if (linked.isPresent()) {
            return members.byId(linked.get());
        }
        PasswordHash password = account.password();
        if (password == null) {
            return Optional.empty();
        }
        Optional<BlysisMembers.Member> same = members.named(account.name())
                .member()
                .filter(member -> password.encoded().equals(member.password()))
                .filter(member -> accountOf(member.id()).isEmpty());
        same.ifPresent(member -> link(account.id(), member.id()));
        return same;
    }

    /**
     * Stores a new account, and makes a password account a member. A name the website holds in any case is taken,
     * even when it took it a moment ago: the plugin's row is taken back, and no member is ever written over.
     */
    @Override
    public Insert insert(Account account) {
        Objects.requireNonNull(account, "account");
        PasswordHash password = account.password();
        if (password == null || !mirrored(account)) {
            return accounts.insert(account);
        }
        BlysisMembers.Named named = members.named(account.name());
        if (named.taken()) {
            return new NameTaken(heldAs(named, account.name()));
        }
        Insert result = accounts.insert(account);
        if (!(result instanceof Inserted)) {
            return result;
        }
        Optional<String> member;
        try {
            member = members.add(account.name(), password, joining(account.name()), clock.instant());
        } catch (RuntimeException failed) {
            accounts.delete(account.id());
            throw failed;
        }
        if (member.isEmpty()) {
            accounts.delete(account.id());
            return new NameTaken(heldAs(members.named(account.name()), account.name()));
        }
        link(account.id(), member.get());
        return result;
    }

    private static PlayerName heldAs(BlysisMembers.Named named, PlayerName fallback) {
        return named.member().flatMap(BlysisAccounts::nameOf).orElse(fallback);
    }

    private BlysisMembers.Joining joining(PlayerName name) {
        return new BlysisMembers.Joining(
                settings.emailOf(name.value()), settings.locale(), settings.currency(), settings.memberRole());
    }

    /**
     * Makes an account the plugin held before the website was on a member, when the website holds nothing under its
     * name; or links it to the member under its name that holds its own hash.
     */
    private void becomeMember(Account account) {
        PasswordHash password = account.password();
        if (password == null || !mirrored(account) || linkOf(account.id()).isPresent()) {
            return;
        }
        BlysisMembers.Named named = members.named(account.name());
        if (named.taken()) {
            memberOf(account);
            return;
        }
        members.add(account.name(), password, joining(account.name()), clock.instant())
                .ifPresent(id -> link(account.id(), id));
    }

    /**
     * Writes every field, for staff tools, and the password to the account's member when it changed. An account
     * without a member becomes one when its name is free on the website.
     */
    @Override
    public void update(Account account) {
        accounts.update(Objects.requireNonNull(account, "account"));
        PasswordHash password = account.password();
        if (password == null || !mirrored(account)) {
            return;
        }
        Optional<BlysisMembers.Member> member = memberOf(account);
        if (member.isEmpty()) {
            becomeMember(account);
        } else if (!member.get().closed()
                && !password.encoded().equals(member.get().password())) {
            members.write(member.get().id(), password, clock.instant());
        }
    }

    @Override
    public void markVerified(UUID id) {
        accounts.markVerified(id);
    }

    /** The website keeps no login times; an account the plugin held before it was on becomes a member here. */
    @Override
    public void recordLogin(UUID id, Instant at, String address) {
        accounts.recordLogin(id, at, address);
        accounts.findById(id).ifPresent(this::becomeMember);
    }

    /**
     * Replaces the password at the member, only while the member still holds {@code expected}: a password changed on
     * the website between the read and this write is kept, and the answer is false. The plugin's row follows.
     */
    @Override
    public boolean replacePassword(UUID id, @Nullable PasswordHash expected, PasswordHash fresh) {
        Objects.requireNonNull(fresh, "fresh");
        Optional<Account> account =
                accounts.findById(Objects.requireNonNull(id, "id")).filter(BlysisAccounts::mirrored);
        Optional<BlysisMembers.Member> member = account.flatMap(this::memberOf);
        if (account.isEmpty() || member.isEmpty()) {
            boolean replaced = accounts.replacePassword(id, expected, fresh);
            if (replaced) {
                account.ifPresent(found -> becomeMember(found.withPassword(fresh)));
            }
            return replaced;
        }
        if (expected == null
                || member.get().closed()
                || !members.replace(member.get().id(), expected.encoded(), fresh, clock.instant())) {
            return false;
        }
        // The plugin's row follows, unless something changed it meanwhile; the member is what a login reads.
        accounts.replacePassword(id, account.get().password(), fresh);
        return true;
    }

    @Override
    public int countRegisteredFrom(String address, Instant since) {
        return accounts.countRegisteredFrom(address, since);
    }

    /**
     * Moves the account to another name and keeps its member, whose username on the website stays as it is. A name
     * another member holds on the website is refused for a password account, as a registration would be.
     */
    @Override
    public Insert rename(UUID id, PlayerName to, Instant at) {
        Objects.requireNonNull(to, "to");
        Optional<Account> moving =
                accounts.findById(Objects.requireNonNull(id, "id")).filter(BlysisAccounts::mirrored);
        if (moving.isPresent()) {
            Optional<String> own = linkOf(id);
            BlysisMembers.Named named = members.named(to);
            boolean theirs = named.taken()
                    && (named.member().isEmpty() || !named.member().get().id().equals(own.orElse(null)));
            if (theirs) {
                return new NameTaken(heldAs(named, to));
            }
        }
        return accounts.rename(id, to, at);
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

    /**
     * Removes the plugin's account and its link, and leaves the member as it is: orders, history and the website
     * login live there. The member still holds a password, so the next connection under its name gets an account
     * again, with that password and nothing else the plugin kept. Closing an account is the website's to do.
     */
    @Override
    public void delete(UUID id) {
        accounts.delete(id);
    }

    /** Removes the idle account as {@link #delete} does; a member who only uses the website is not touched. */
    @Override
    public boolean deleteIfInactive(UUID id, Instant before) {
        return accounts.deleteIfInactive(id, before);
    }

    @Override
    public void moveId(UUID from, UUID to) {
        accounts.moveId(from, to);
    }

    /**
     * Whether the account's member is banned or deleted on the website, or gone from it. An account with no member
     * answers as the plugin's own accounts do.
     */
    @Override
    public boolean closed(UUID id) {
        Optional<String> linked = linkOf(Objects.requireNonNull(id, "id"));
        if (linked.isEmpty()) {
            return accounts.closed(id);
        }
        return members.byId(linked.get()).map(BlysisMembers.Member::closed).orElse(true);
    }

    /** Closes the pool on the website's database when this opened it. */
    @Override
    public void close() {
        if (site != null) {
            site.close();
        }
    }

    private Optional<String> linkOf(UUID account) {
        return own.transaction(tx -> tx.queryFirst(
                "SELECT member_id FROM uxmauth_blysis_link WHERE account_id = ?",
                statement -> statement.setString(1, account.toString()),
                row -> row.getString("member_id")));
    }

    private Optional<UUID> accountOf(String member) {
        return own.transaction(tx -> tx.queryFirst(
                "SELECT account_id FROM uxmauth_blysis_link WHERE member_id = ?",
                statement -> statement.setString(1, member),
                row -> UUID.fromString(row.getString("account_id"))));
    }

    /**
     * Records that the member is the account's. Two servers that adopt the same member at once both try; the one
     * that finds the link already made by the other goes on.
     */
    private void link(UUID account, String member) {
        String id = account.toString();
        try {
            own.inTransaction(tx -> {
                int changed =
                        tx.update("UPDATE uxmauth_blysis_link SET member_id = ? WHERE account_id = ?", statement -> {
                            statement.setString(1, member);
                            statement.setString(2, id);
                        });
                if (changed == 0) {
                    tx.update("INSERT INTO uxmauth_blysis_link (account_id, member_id) VALUES (?, ?)", statement -> {
                        statement.setString(1, id);
                        statement.setString(2, member);
                    });
                }
            });
        } catch (StorageException clash) {
            if (!linkOf(account).filter(member::equals).isPresent()) {
                throw clash;
            }
        }
    }
}
