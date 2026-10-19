package com.uxplima.auth.application.account;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.auth.application.ChangePassword;
import com.uxplima.auth.application.ChangeResult;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.staff.MarkKind;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.state.AuthState;

/**
 * A premium player turns their own account cracked. A cracked account logs in with a password, so the player types
 * it: the account's own when it has one, which proves the player at the keyboard is its owner, or its first one,
 * which the password rules check like any other. The switch itself is {@link MarkKind#cracked}, so the player is
 * sent to join again as cracked.
 */
public final class SwitchToCracked {

    /** What a switch came to. */
    public sealed interface Switched {
        /** The account is cracked, and its player joins again. */
        record Done() implements Switched {}

        /** The password typed is not the account's. */
        record WrongPassword() implements Switched {}

        /** The first password typed breaks a rule. */
        record Refused(PasswordVerdict verdict) implements Switched {}

        /** The account is not premium. */
        record NotPremium() implements Switched {}

        /** The player is not logged in. */
        record NotLoggedIn() implements Switched {}
    }

    private final Sessions sessions;
    private final AccountRepository accounts;
    private final Hashing hashing;
    private final HashingLane lane;
    private final ChangePassword change;
    private final MarkKind mark;

    /** @param change sets the first password of an account that has none, on the password rules */
    public SwitchToCracked(
            Sessions sessions,
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            ChangePassword change,
            MarkKind mark) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.change = Objects.requireNonNull(change, "change");
        this.mark = Objects.requireNonNull(mark, "mark");
    }

    /** Switches the logged-in player of this connection, proven by the password typed. Hashing runs on the lane. */
    public CompletableFuture<Switched> confirm(UUID connection, String typed) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(typed, "typed");
        Optional<Account> owner = sessions.state(connection)
                .filter(AuthState.Authenticated.class::isInstance)
                .flatMap(state -> accounts.findById(
                        ((AuthState.Authenticated) state).account().id()));
        if (owner.isEmpty()) {
            return CompletableFuture.completedFuture(new Switched.NotLoggedIn());
        }
        Account account = owner.get();
        if (account.kind() != AccountKind.PREMIUM) {
            return CompletableFuture.completedFuture(new Switched.NotPremium());
        }
        PasswordHash stored = account.password();
        if (stored == null) {
            return change.replace(connection, Password.of(typed)).thenApply(result -> afterFirst(account, result));
        }
        return lane.submit(() -> matches(typed, stored) ? switched(account) : new Switched.WrongPassword());
    }

    private Switched afterFirst(Account account, ChangeResult result) {
        return switch (result) {
            case ChangeResult.Changed changed -> switched(account);
            case ChangeResult.Replaced replaced -> switched(account);
            case ChangeResult.Rejected rejected -> new Switched.Refused(rejected.verdict());
            case ChangeResult.WrongOld wrongOld -> new Switched.WrongPassword();
            case ChangeResult.NotLoggedIn notLoggedIn -> new Switched.NotLoggedIn();
            case ChangeResult.Gone gone -> new Switched.NotLoggedIn();
        };
    }

    private Switched switched(Account account) {
        return mark.cracked(account.name(), Bulletin.By.OWNER) == MarkKind.Marked.DONE
                ? new Switched.Done()
                : new Switched.NotPremium();
    }

    private boolean matches(String typed, PasswordHash stored) {
        try (Password password = Password.of(typed)) {
            return !(hashing.verify(password, stored) instanceof Verification.Wrong);
        }
    }
}
