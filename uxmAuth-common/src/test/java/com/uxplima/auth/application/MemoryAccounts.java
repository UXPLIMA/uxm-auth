package com.uxplima.auth.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import org.jspecify.annotations.Nullable;

/** Accounts in a map, enough for the paths that log in, change a password or prove an account. */
public final class MemoryAccounts implements AccountRepository {

    private final Map<UUID, Account> byId = new ConcurrentHashMap<>();

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
        byId.put(account.id(), account);
        return new Inserted();
    }

    @Override
    public void update(Account account) {
        byId.put(account.id(), account);
    }

    @Override
    public void markVerified(UUID id) {
        byId.computeIfPresent(id, (key, account) -> account.asVerified());
    }

    @Override
    public void recordLogin(UUID id, Instant at, String address) {
        byId.computeIfPresent(id, (key, account) -> account.loggedIn(at, address));
    }

    @Override
    public boolean replacePassword(UUID id, @Nullable PasswordHash expected, PasswordHash fresh) {
        boolean[] replaced = {false};
        byId.computeIfPresent(id, (key, account) -> {
            if (!Objects.equals(account.password(), expected)) {
                return account;
            }
            replaced[0] = true;
            return account.withPassword(fresh);
        });
        return replaced[0];
    }

    @Override
    public int countRegisteredFrom(String address, Instant since) {
        return 0;
    }

    @Override
    public Optional<Account> findByPremiumId(UUID premiumId) {
        return Optional.empty();
    }

    @Override
    public Insert rename(UUID id, PlayerName to, Instant at) {
        return new Inserted();
    }

    @Override
    public Optional<PlayerName> renamedFrom(PlayerName old) {
        return Optional.empty();
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
    public void moveId(UUID from, UUID to) {}
}
