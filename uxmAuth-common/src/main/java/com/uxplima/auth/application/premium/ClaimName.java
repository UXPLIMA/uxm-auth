package com.uxplima.auth.application.premium;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.state.AuthState;

/**
 * "This name is mine." From the door, by a player who cannot log in to the account under it, the claim asks only
 * their next connection from this address for the handshake, so nobody can hand the cracked holder a failed
 * one. From inside the account, logged in, it is the owner's own request: their next connection is asked, and
 * when it passes the account turns premium and keeps everything.
 *
 * <p>Under the strict rule every paid name is asked anyway, and by domain the address decides, so neither takes
 * a claim.
 */
public final class ClaimName {

    /** What the claim came to. */
    public enum Result {
        /** The next connection from here is asked for the handshake. */
        ASKED,
        /** The account proved its name already. */
        ALREADY_PREMIUM,
        /** The operator's rule proves names another way. */
        NOT_THIS_RULE,
        /** The connection has no session. */
        GONE
    }

    private final Sessions sessions;
    private final AtTheDoor door;
    private final Supplier<NameRule> rule;

    public ClaimName(Sessions sessions, AtTheDoor door, Supplier<NameRule> rule) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.door = Objects.requireNonNull(door, "door");
        this.rule = Objects.requireNonNull(rule, "rule");
    }

    /** The claim of the connection with this session, made from this address. */
    public Result claim(UUID connection, String address) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(address, "address");
        if (!takesClaims()) {
            return Result.NOT_THIS_RULE;
        }
        AuthState state = sessions.state(connection).orElse(null);
        return switch (state) {
            case null -> Result.GONE;
            case AuthState.Authenticated in when in.account().kind() != AccountKind.CRACKED -> Result.ALREADY_PREMIUM;
            case AuthState.Authenticated in -> {
                door.ownerAsks(address, in.account().name());
                yield Result.ASKED;
            }
            case AuthState.Registered registered ->
                asked(address, registered.account().name());
            case AuthState.Unregistered unregistered -> asked(address, unregistered.name());
        };
    }

    /** Whether the operator's rule takes a claim at all, which decides whether a screen offers one. */
    public boolean takesClaims() {
        NameRule now = rule.get();
        return now == NameRule.AUTOMATIC || now == NameRule.OPT_IN;
    }

    private Result asked(String address, PlayerName name) {
        door.claim(address, name);
        return Result.ASKED;
    }
}
