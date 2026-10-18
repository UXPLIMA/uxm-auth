package com.uxplima.auth.application.staff;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;

/**
 * Staff let a waiting player in, past the password and any code. Staff may stand on another server than the
 * player, so the request is a bulletin, and the member the player waits on lets them in.
 */
public final class ForceLogin {

    /** What asking came to. */
    public enum Asked {
        /** Every member was told; the one the player waits on lets them in. */
        ASKED,
        /** No account is under the name. */
        UNKNOWN
    }

    private final AccountRepository accounts;
    private final Sessions sessions;
    private final NetworkEvents events;
    private final Clock clock;

    public ForceLogin(AccountRepository accounts, Sessions sessions, NetworkEvents events, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Tells every member to let the player of the account under this name in. Reads the table. */
    public Asked ask(PlayerName name) {
        Optional<Account> account = accounts.findByName(Objects.requireNonNull(name, "name"));
        account.ifPresent(found -> events.publish(new Bulletin.LetIn(found.id())));
        return account.isPresent() ? Asked.ASKED : Asked.UNKNOWN;
    }

    /**
     * Lets the connection in as forced when it waits with an account, and keeps the login on the account. Empty
     * when it has no account, is in already, or has no session. Writes the table.
     */
    public Optional<AuthState.Authenticated> admit(UUID connection, String address) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(address, "address");
        Instant now = clock.instant();
        AtomicReference<AuthState.Authenticated> let = new AtomicReference<>();
        sessions.update(
                connection,
                state -> waiting(state)
                        .map(account -> {
                            AuthState.Authenticated in =
                                    new AuthState.Authenticated(account.loggedIn(now, address), AuthMethod.FORCED);
                            let.set(in);
                            return (AuthState) in;
                        })
                        .orElse(state));
        Optional<AuthState.Authenticated> in = Optional.ofNullable(let.get());
        in.ifPresent(found -> accounts.recordLogin(found.account().id(), now, address));
        return in;
    }

    /** The account a waiting state holds, whatever it waits for. */
    private static Optional<Account> waiting(AuthState state) {
        return switch (state) {
            case AuthState.Registered registered -> Optional.of(registered.account());
            case AuthState.Replacing replacing -> Optional.of(replacing.account());
            case AuthState.SecondFactor second -> Optional.of(second.account());
            case AuthState.SettingUp setting -> Optional.of(setting.account());
            case AuthState.Unregistered unregistered -> Optional.empty();
            case AuthState.Authenticated authenticated -> Optional.empty();
        };
    }
}
