package com.uxplima.auth.testing;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;

/** Accounts in memory, with the same one-row-per-name rule the SQL table keeps, and a count of writes. */
public final class InMemoryAccounts implements AccountRepository {

    private final Map<String, Account> byKey = new ConcurrentHashMap<>();
    private final AtomicInteger writes = new AtomicInteger();

    @Override
    public Optional<Account> findByName(PlayerName name) {
        return Optional.ofNullable(byKey.get(name.key()));
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return byKey.values().stream()
                .filter(account -> account.id().equals(id))
                .findFirst();
    }

    @Override
    public Insert insert(Account account) {
        writes.incrementAndGet();
        Account holder = byKey.putIfAbsent(account.name().key(), account);
        return holder == null ? new Inserted() : new NameTaken(holder.name());
    }

    @Override
    public void update(Account account) {
        writes.incrementAndGet();
        byKey.put(account.name().key(), account);
    }

    @Override
    public int countRegisteredFrom(String address) {
        return (int) byKey.values().stream()
                .filter(account -> account.registrationAddress().equals(address))
                .count();
    }

    /** Puts an account in without counting it, as the state before a test. */
    public void seed(Account account) {
        byKey.put(account.name().key(), account);
    }

    /** How many inserts and updates reached this store. */
    public int writes() {
        return writes.get();
    }
}
