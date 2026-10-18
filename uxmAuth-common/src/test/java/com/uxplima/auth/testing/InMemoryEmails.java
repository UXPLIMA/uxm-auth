package com.uxplima.auth.testing;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.domain.mail.EmailAddress;

/** The mail table in memory, for the use cases' tests. */
public final class InMemoryEmails implements Emails {

    private record Key(UUID account, Purpose purpose) {}

    private final Map<UUID, EmailAddress> addresses = new ConcurrentHashMap<>();
    private final Map<Key, Code> codes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> loginCodes = new ConcurrentHashMap<>();

    @Override
    public Optional<EmailAddress> address(UUID account) {
        return Optional.ofNullable(addresses.get(account));
    }

    @Override
    public void setAddress(UUID account, EmailAddress address) {
        addresses.compute(account, (id, before) -> address);
    }

    @Override
    public void removeAddress(UUID account) {
        addresses.compute(account, (id, before) -> null);
        loginCodes.compute(account, (id, before) -> null);
    }

    @Override
    public boolean mailsLoginCode(UUID account) {
        return addresses.containsKey(account) && loginCodes.getOrDefault(account, false);
    }

    @Override
    public void setMailsLoginCode(UUID account, boolean on) {
        loginCodes.compute(account, (id, before) -> on);
    }

    @Override
    public Optional<Code> code(UUID account, Purpose purpose) {
        return Optional.ofNullable(codes.get(new Key(account, purpose)));
    }

    @Override
    public Optional<Code> changeCode(UUID account, Purpose purpose, UnaryOperator<Optional<Code>> change) {
        AtomicReference<Optional<Code>> after = new AtomicReference<>(Optional.empty());
        codes.compute(new Key(account, purpose), (key, before) -> {
            Optional<Code> changed = change.apply(Optional.ofNullable(before));
            after.set(changed);
            return changed.orElse(null);
        });
        return Objects.requireNonNull(after.get(), "after");
    }
}
