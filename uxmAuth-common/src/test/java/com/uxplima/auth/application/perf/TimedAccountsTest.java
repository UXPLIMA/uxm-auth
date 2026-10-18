package com.uxplima.auth.application.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.InMemoryAccounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every call on the accounts table is timed as database work, and answers what the table answers. */
final class TimedAccountsTest {

    private final AtomicLong clock = new AtomicLong();
    private final Timings timings = new Timings(() -> clock.addAndGet(1000));
    private final InMemoryAccounts table = new InMemoryAccounts();
    private final AccountRepository accounts = new TimedAccounts(table, timings);

    private long timed() {
        return timings.report(Timings.Kind.DATABASE).sinceStart().count();
    }

    @Test
    @DisplayName("every call is one database sample, and the answers are the table's own")
    void everyCallIsTimed() {
        Account steve = AuthFixture.account("Steve", new PasswordHash("old"));
        UUID id = steve.id();

        assertThat(accounts.insert(steve)).isInstanceOf(AccountRepository.Inserted.class);
        assertThat(accounts.findByName(new PlayerName("steve"))).contains(steve);
        assertThat(accounts.findById(id)).contains(steve);
        accounts.update(steve);
        accounts.recordLogin(id, AuthFixture.NOW, "203.0.113.7");
        assertThat(accounts.replacePassword(id, new PasswordHash("old"), new PasswordHash("new")))
                .isTrue();
        assertThat(accounts.countRegisteredFrom("10.0.0.1", Instant.EPOCH))
                .isEqualTo(table.countRegisteredFrom("10.0.0.1", Instant.EPOCH));
        assertThat(accounts.findByPremiumId(UUID.randomUUID())).isEmpty();
        assertThat(accounts.rename(id, new PlayerName("Steven"), AuthFixture.NOW))
                .isInstanceOf(AccountRepository.Inserted.class);
        assertThat(accounts.renamedFrom(new PlayerName("Steve"))).isEqualTo(table.renamedFrom(new PlayerName("Steve")));
        assertThat(accounts.untoldRename(id)).isEqualTo(table.untoldRename(id));
        accounts.markRenameTold(id);
        UUID other = UUID.randomUUID();
        accounts.moveId(id, other);
        accounts.delete(other);

        assertThat(timed()).as("fourteen calls on the table").isEqualTo(14);
        assertThat(table.findById(other)).isEmpty();
    }

    @Test
    @DisplayName("a call the table refuses is timed too, and its error reaches the caller")
    void aFailingCallIsTimed() {
        AccountRepository refusing = (AccountRepository) java.lang.reflect.Proxy.newProxyInstance(
                AccountRepository.class.getClassLoader(),
                new Class<?>[] {AccountRepository.class},
                (proxy, method, args) -> {
                    throw new IllegalStateException("the database is gone");
                });
        AccountRepository broken = new TimedAccounts(refusing, timings);

        assertThatThrownBy(() -> broken.delete(UUID.randomUUID())).hasMessageContaining("the database is gone");
        assertThat(timed()).isEqualTo(1);
    }
}
