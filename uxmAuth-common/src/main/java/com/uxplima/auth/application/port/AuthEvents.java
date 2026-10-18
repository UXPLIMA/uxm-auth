package com.uxplima.auth.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;

/**
 * What the use cases tell the platform's event bus, for another plugin to hear. The two that come before a login
 * or a registration may refuse it with a reason; they are asked off the server's threads and may wait on a
 * listener. The rest only tell, on the thread the change happened on, which is never a server thread.
 */
public interface AuthEvents {

    /** Tells nobody and refuses nothing. */
    AuthEvents NONE = new AuthEvents() {
        @Override
        public Optional<String> beforeLogin(UUID connection) {
            return Optional.empty();
        }

        @Override
        public Optional<String> beforeRegister(UUID connection) {
            return Optional.empty();
        }

        @Override
        public void wrongPassword(UUID connection, int attemptsLeft) {}

        @Override
        public void passwordChanged(UUID account, PlayerName name) {}

        @Override
        public void unregistered(UUID account, PlayerName name) {}

        @Override
        public void kindSwitched(UUID account, PlayerName name, AccountKind to) {}
    };

    /** Before a login's password is checked: the reason a listener refused it, if one did. */
    Optional<String> beforeLogin(UUID connection);

    /** Before an account is made: the reason a listener refused it, if one did. */
    Optional<String> beforeRegister(UUID connection);

    void wrongPassword(UUID connection, int attemptsLeft);

    void passwordChanged(UUID account, PlayerName name);

    void unregistered(UUID account, PlayerName name);

    void kindSwitched(UUID account, PlayerName name, AccountKind to);
}
