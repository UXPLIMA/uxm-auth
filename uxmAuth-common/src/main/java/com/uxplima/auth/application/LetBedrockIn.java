package com.uxplima.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;

/**
 * A Bedrock player Floodgate vouched for, through Xbox Live, at the door.
 *
 * <p>Their own Bedrock account lets them in, and a first visit makes one with no password. A player Floodgate
 * linked to a Java account gets into that Java account, because the link is Floodgate's proof that the two are
 * one person. Either way the account's id must be the id Floodgate gave the connection. An unlinked player whose name is a Java account's is asked for that account's password like
 * anyone else: Xbox Live proves who they are on Bedrock, not that the Java account is theirs.
 *
 * <p>This runs on the thread the server gives the pre-login, which may wait on the database.
 */
public final class LetBedrockIn {

    private final AccountRepository accounts;
    private final IntSupplier maxAttempts;
    private final Clock clock;

    public LetBedrockIn(AccountRepository accounts, IntSupplier maxAttempts, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.maxAttempts = Objects.requireNonNull(maxAttempts, "maxAttempts");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The state this connection opens with. */
    public AuthState admit(UUID connection, PlayerName name, boolean linked, String address) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        Optional<Account> found = accounts.findByName(name);
        if (found.isEmpty()) {
            Account fresh = new Account(
                    connection, name, AccountKind.BEDROCK, null, null, clock.instant(), address, null, null);
            if (accounts.insert(fresh) instanceof AccountRepository.Inserted) {
                return new AuthState.Authenticated(fresh.loggedIn(clock.instant(), address), AuthMethod.BEDROCK);
            }
            // Another connection made the account a moment ago: read it as it is now.
            found = accounts.findByName(name);
        }
        Account account = found.orElseThrow(() -> new IllegalStateException("the account of " + name + " vanished"));
        // Floodgate's id is the proof: the player's own Xbox identity, or the Java account it is linked to. A
        // name alone proves nothing, since a gamertag can change hands.
        boolean theirs = account.id().equals(connection) && (account.kind() == AccountKind.BEDROCK || linked);
        if (!theirs) {
            return new AuthState.Registered(account, Attempts.fresh(maxAttempts.getAsInt()));
        }
        Instant now = clock.instant();
        accounts.recordLogin(account.id(), now, address);
        return new AuthState.Authenticated(account.loggedIn(now, address), AuthMethod.BEDROCK);
    }
}
