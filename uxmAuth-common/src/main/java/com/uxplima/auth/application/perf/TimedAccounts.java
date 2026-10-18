package com.uxplima.auth.application.perf;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import org.jspecify.annotations.Nullable;

/** The accounts table, with every call timed as database work, a call that fails among them. */
public final class TimedAccounts implements AccountRepository {

    private final AccountRepository table;
    private final Timings timings;

    public TimedAccounts(AccountRepository table, Timings timings) {
        this.table = Objects.requireNonNull(table, "table");
        this.timings = Objects.requireNonNull(timings, "timings");
    }

    private void done(long start) {
        timings.stop(Timings.Kind.DATABASE, start);
    }

    @Override
    public Optional<Account> findByName(PlayerName name) {
        long start = timings.start();
        try {
            return table.findByName(name);
        } finally {
            done(start);
        }
    }

    @Override
    public Optional<Account> findById(UUID id) {
        long start = timings.start();
        try {
            return table.findById(id);
        } finally {
            done(start);
        }
    }

    @Override
    public Insert insert(Account account) {
        long start = timings.start();
        try {
            return table.insert(account);
        } finally {
            done(start);
        }
    }

    @Override
    public void update(Account account) {
        long start = timings.start();
        try {
            table.update(account);
        } finally {
            done(start);
        }
    }

    @Override
    public void recordLogin(UUID id, Instant at, String address) {
        long start = timings.start();
        try {
            table.recordLogin(id, at, address);
        } finally {
            done(start);
        }
    }

    @Override
    public boolean replacePassword(UUID id, @Nullable PasswordHash expected, PasswordHash fresh) {
        long start = timings.start();
        try {
            return table.replacePassword(id, expected, fresh);
        } finally {
            done(start);
        }
    }

    @Override
    public int countRegisteredFrom(String address, Instant since) {
        long start = timings.start();
        try {
            return table.countRegisteredFrom(address, since);
        } finally {
            done(start);
        }
    }

    @Override
    public Optional<Account> findByPremiumId(UUID premiumId) {
        long start = timings.start();
        try {
            return table.findByPremiumId(premiumId);
        } finally {
            done(start);
        }
    }

    @Override
    public Insert rename(UUID id, PlayerName to, Instant at) {
        long start = timings.start();
        try {
            return table.rename(id, to, at);
        } finally {
            done(start);
        }
    }

    @Override
    public Optional<PlayerName> renamedFrom(PlayerName old) {
        long start = timings.start();
        try {
            return table.renamedFrom(old);
        } finally {
            done(start);
        }
    }

    @Override
    public Optional<Renamed> untoldRename(UUID id) {
        long start = timings.start();
        try {
            return table.untoldRename(id);
        } finally {
            done(start);
        }
    }

    @Override
    public void markRenameTold(UUID id) {
        long start = timings.start();
        try {
            table.markRenameTold(id);
        } finally {
            done(start);
        }
    }

    @Override
    public void delete(UUID id) {
        long start = timings.start();
        try {
            table.delete(id);
        } finally {
            done(start);
        }
    }

    @Override
    public void moveId(UUID from, UUID to) {
        long start = timings.start();
        try {
            table.moveId(from, to);
        } finally {
            done(start);
        }
    }
}
