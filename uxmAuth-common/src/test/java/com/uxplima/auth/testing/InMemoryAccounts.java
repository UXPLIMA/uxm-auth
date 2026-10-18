package com.uxplima.auth.testing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import org.jspecify.annotations.Nullable;

/** Accounts in memory, with the same one-row-per-name rule the SQL table keeps, and a count of writes. */
public final class InMemoryAccounts implements AccountRepository {

    private final Map<String, Account> byKey = new ConcurrentHashMap<>();
    private final AtomicInteger writes = new AtomicInteger();
    private final List<Move> moves = new ArrayList<>();

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
    public void recordLogin(UUID id, Instant at, String address) {
        writes.incrementAndGet();
        findById(id).ifPresent(account -> byKey.put(account.name().key(), account.loggedIn(at, address)));
    }

    @Override
    public synchronized boolean replacePassword(UUID id, @Nullable PasswordHash expected, PasswordHash fresh) {
        writes.incrementAndGet();
        Optional<Account> held = findById(id).filter(account -> Objects.equals(account.password(), expected));
        held.ifPresent(account -> byKey.put(account.name().key(), account.withPassword(fresh)));
        return held.isPresent();
    }

    @Override
    public int countRegisteredFrom(String address, java.time.Instant since) {
        String key = com.uxplima.auth.domain.protection.AddressKey.of(address);
        return (int) byKey.values().stream()
                .filter(account -> com.uxplima.auth.domain.protection.AddressKey.of(account.registrationAddress())
                        .equals(key))
                .filter(account -> account.kind() == com.uxplima.auth.domain.account.AccountKind.CRACKED)
                .filter(account -> !account.registeredAt().isBefore(since))
                .count();
    }

    @Override
    public Optional<Account> findByPremiumId(UUID premiumId) {
        return byKey.values().stream()
                .filter(account -> premiumId.equals(account.premiumId()))
                .findFirst();
    }

    @Override
    public synchronized Insert rename(UUID id, PlayerName to, Instant at) {
        writes.incrementAndGet();
        Account account = findById(id).orElseThrow();
        Account holder = byKey.get(to.key());
        if (holder != null && !holder.id().equals(id)) {
            return new NameTaken(holder.name());
        }
        byKey.remove(account.name().key());
        byKey.put(to.key(), withName(account, to));
        moves.add(new Move(id, account.name(), to, at));
        return new Inserted();
    }

    @Override
    public synchronized Optional<PlayerName> renamedFrom(PlayerName old) {
        for (int i = moves.size() - 1; i >= 0; i--) {
            Move move = moves.get(i);
            if (move.from().key().equals(old.key())) {
                return findById(move.id()).map(Account::name);
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized Optional<Renamed> untoldRename(UUID id) {
        for (int i = moves.size() - 1; i >= 0; i--) {
            Move move = moves.get(i);
            if (move.id().equals(id) && !move.told()) {
                return Optional.of(new Renamed(move.from(), move.to()));
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized void markRenameTold(UUID id) {
        moves.replaceAll(move -> move.id().equals(id) ? move.toldNow() : move);
    }

    @Override
    public synchronized void delete(UUID id) {
        writes.incrementAndGet();
        findById(id).ifPresent(account -> byKey.remove(account.name().key()));
        moves.removeIf(move -> move.id().equals(id));
    }

    @Override
    public synchronized void moveId(UUID from, UUID to) {
        writes.incrementAndGet();
        findById(from).ifPresent(account -> byKey.put(account.name().key(), withId(account, to)));
        moves.replaceAll(move -> move.id().equals(from) ? move.under(to) : move);
    }

    private record Move(UUID id, PlayerName from, PlayerName to, Instant at, boolean told) {
        Move(UUID id, PlayerName from, PlayerName to, Instant at) {
            this(id, from, to, at, false);
        }

        Move toldNow() {
            return new Move(id, from, to, at, true);
        }

        Move under(UUID other) {
            return new Move(other, from, to, at, told);
        }
    }

    private static Account withName(Account account, PlayerName name) {
        return new Account(
                account.id(),
                name,
                account.kind(),
                account.premiumId(),
                account.password(),
                account.registeredAt(),
                account.registrationAddress(),
                account.lastLoginAt(),
                account.lastAddress(),
                account.unverified());
    }

    private static Account withId(Account account, UUID id) {
        return new Account(
                id,
                account.name(),
                account.kind(),
                account.premiumId(),
                account.password(),
                account.registeredAt(),
                account.registrationAddress(),
                account.lastLoginAt(),
                account.lastAddress(),
                account.unverified());
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
