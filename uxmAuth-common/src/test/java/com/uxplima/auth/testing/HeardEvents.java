package com.uxplima.auth.testing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.uxplima.auth.application.port.AuthEvents;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import org.jspecify.annotations.Nullable;

/** The events a use case fires, written down with the thread each fired on; a refusal is set by the test. */
public final class HeardEvents implements AuthEvents {

    public final List<String> heard = new CopyOnWriteArrayList<>();
    public final List<String> threads = new CopyOnWriteArrayList<>();
    public volatile @Nullable String refuseLogin;
    public volatile @Nullable String refuseRegister;

    @Override
    public Optional<String> beforeLogin(UUID connection) {
        heard.add("before-login");
        threads.add(Thread.currentThread().getName());
        return Optional.ofNullable(refuseLogin);
    }

    @Override
    public Optional<String> beforeRegister(UUID connection) {
        heard.add("before-register");
        threads.add(Thread.currentThread().getName());
        return Optional.ofNullable(refuseRegister);
    }

    @Override
    public void wrongPassword(UUID connection, int attemptsLeft) {
        heard.add("wrong " + attemptsLeft);
    }

    @Override
    public void passwordChanged(UUID account, PlayerName name) {
        heard.add("changed " + name.value());
    }

    @Override
    public void unregistered(UUID account, PlayerName name) {
        heard.add("unregistered " + name.value());
    }

    @Override
    public void kindSwitched(UUID account, PlayerName name, AccountKind to) {
        heard.add("switched " + name.value() + " " + to);
    }
}
