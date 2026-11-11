package com.uxplima.auth.application.premium;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.Disputes;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import org.jspecify.annotations.Nullable;

/**
 * Accounts in memory that refuse what the database refuses: a name held in any case, and an id held already, which
 * the database answers with an error rather than an outcome.
 */
public final class MemoryAccounts implements AccountRepository {

    public static final Instant THEN = Instant.parse("2030-01-01T12:00:00Z");

    private final Map<UUID, Account> byId = new LinkedHashMap<>();
    private final Map<String, PlayerName> movedFrom = new HashMap<>();

    /** A cracked account with a password, under this id and name. */
    public Account cracked(UUID id, String name) {
        Account account = new Account(
                id,
                new PlayerName(name),
                AccountKind.CRACKED,
                null,
                new PasswordHash("$2a$04$hash"),
                THEN,
                "198.51.100.1",
                null,
                null);
        insert(account);
        return account;
    }

    public List<Account> all() {
        return new ArrayList<>(byId.values());
    }

    @Override
    public Optional<Account> findByName(PlayerName name) {
        return byId.values().stream()
                .filter(account -> account.name().key().equals(name.key()))
                .findFirst();
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Insert insert(Account account) {
        Optional<Account> holder = findByName(account.name());
        if (holder.isPresent()) {
            return new NameTaken(holder.get().name());
        }
        if (byId.containsKey(account.id())) {
            throw new IllegalStateException("primary key " + account.id() + " is held");
        }
        byId.put(account.id(), account);
        return new Inserted();
    }

    @Override
    public void update(Account account) {
        if (!byId.containsKey(account.id())) {
            throw new IllegalStateException("no account " + account.id());
        }
        byId.put(account.id(), account);
    }

    @Override
    public void markVerified(UUID id) {
        byId.computeIfPresent(id, (key, account) -> account.asVerified());
    }

    @Override
    public void recordLogin(UUID id, Instant at, String address) {
        Account account = Objects.requireNonNull(byId.get(id), "account");
        byId.put(id, account.loggedIn(at, address));
    }

    @Override
    public boolean replacePassword(UUID id, @Nullable PasswordHash expected, PasswordHash fresh) {
        return false;
    }

    @Override
    public int countRegisteredFrom(String address, Instant since) {
        return 0;
    }

    @Override
    public Optional<Account> findByPremiumId(UUID premiumId) {
        return byId.values().stream()
                .filter(account -> premiumId.equals(account.premiumId()))
                .findFirst();
    }

    @Override
    public Insert rename(UUID id, PlayerName to, Instant at) {
        Optional<Account> holder = findByName(to).filter(found -> !found.id().equals(id));
        if (holder.isPresent()) {
            return new NameTaken(holder.get().name());
        }
        Account account = Objects.requireNonNull(byId.get(id), "account");
        movedFrom.put(account.name().key(), to);
        byId.put(
                id,
                new Account(
                        id,
                        to,
                        account.kind(),
                        account.premiumId(),
                        account.password(),
                        account.registeredAt(),
                        account.registrationAddress(),
                        account.lastLoginAt(),
                        account.lastAddress(),
                        account.unverified()));
        return new Inserted();
    }

    @Override
    public Optional<PlayerName> renamedFrom(PlayerName old) {
        return Optional.ofNullable(movedFrom.get(old.key()));
    }

    @Override
    public Optional<Renamed> untoldRename(UUID id) {
        return Optional.empty();
    }

    @Override
    public void markRenameTold(UUID id) {}

    @Override
    public void delete(UUID id) {
        byId.remove(id);
    }

    @Override
    public void moveId(UUID from, UUID to) {
        if (byId.containsKey(to)) {
            throw new IllegalStateException("primary key " + to + " is held");
        }
        Account account = Objects.requireNonNull(byId.remove(from), "account");
        byId.put(
                to,
                new Account(
                        to,
                        account.name(),
                        account.kind(),
                        account.premiumId(),
                        account.password(),
                        account.registeredAt(),
                        account.registrationAddress(),
                        account.lastLoginAt(),
                        account.lastAddress(),
                        account.unverified()));
    }

    /** No name is in dispute, and none is ever raised. */
    public static final class NoDisputes implements Disputes {

        @Override
        public void raise(PlayerName name, UUID premiumId, Instant at) {}

        @Override
        public Optional<Dispute> of(PlayerName name) {
            return Optional.empty();
        }

        @Override
        public void resolve(PlayerName name) {}

        @Override
        public void keep(PlayerName name, UUID holder) {}
    }

    @Override
    public boolean closed(UUID id) {
        return false;
    }
}
