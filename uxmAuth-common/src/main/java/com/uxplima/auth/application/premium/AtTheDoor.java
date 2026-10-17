package com.uxplima.auth.application.premium;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.NameVerdict;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.Arrival;
import com.uxplima.auth.domain.premium.Decision;
import com.uxplima.auth.domain.premium.Holder;
import com.uxplima.auth.domain.premium.NameOwnership;

/**
 * A connection at the hello, before the server makes a profile for it: is it asked for the handshake?
 *
 * <p>The account is read first, and the web only where the operator's rule needs it. A name the door will
 * refuse anyway, for its spelling or for the case it was registered in, is never asked about: the name check
 * at the pre-login says why, and the web and Mojang's cap are not spent on it. This may wait on the database
 * and the network, so it never runs on a thread the server needs.
 */
public final class AtTheDoor {

    private final AccountRepository accounts;
    private final Supplier<NamePolicy> names;
    private final PremiumLookup lookup;
    private final Supplier<NameOwnership> ownership;
    private final NameMemory failed;
    private final NameMemory claims;

    public AtTheDoor(
            AccountRepository accounts,
            Supplier<NamePolicy> names,
            PremiumLookup lookup,
            Supplier<NameOwnership> ownership,
            NameMemory failed,
            NameMemory claims) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.names = Objects.requireNonNull(names, "names");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.failed = Objects.requireNonNull(failed, "failed");
        this.claims = Objects.requireNonNull(claims, "claims");
    }

    /** What the door does with a connection under this name, from this address, through this host. */
    public Decision decide(String raw, String address, String host) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(host, "host");
        if (!(names.get().check(raw) instanceof NameVerdict.Accepted accepted)) {
            return new Decision.Cracked(false, false);
        }
        PlayerName name = accepted.name();
        Optional<Account> account = accounts.findByName(name);
        if (account.isPresent() && !account.get().name().value().equals(raw)) {
            return new Decision.Cracked(false, false);
        }
        boolean claimed = claims.holds(address, name);
        Arrival arrival = new Arrival(holder(account), claimed, failed.holds(address, name), host);
        Decision decision = ownership.get().decide(arrival, () -> lookup.lookup(name));
        if (claimed && decision instanceof Decision.Handshake) {
            // A claim buys one try. Passed, the name changes hands; failed, the cracked owner keeps it.
            claims.forget(address, name);
        }
        return decision;
    }

    /** A player at this address says the name is theirs: their next connection from here is asked. */
    public void claim(String address, PlayerName name) {
        claims.remember(address, name);
    }

    /** A connection from this address under this name failed the handshake. */
    public void failed(String address, PlayerName name) {
        failed.remember(address, name);
    }

    /** A connection from this address under this name passed the handshake. */
    public void passed(String address, PlayerName name) {
        failed.forget(address, name);
    }

    private static Holder holder(Optional<Account> account) {
        if (account.isEmpty()) {
            return Holder.NOBODY;
        }
        return switch (account.get().kind()) {
            case PREMIUM -> Holder.PREMIUM;
            case BEDROCK -> Holder.BEDROCK;
            case CRACKED -> account.get().unverified() ? Holder.CRACKED_UNVERIFIED : Holder.CRACKED;
        };
    }
}
