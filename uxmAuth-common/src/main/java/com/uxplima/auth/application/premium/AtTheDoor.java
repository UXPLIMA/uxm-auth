package com.uxplima.auth.application.premium;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.Refusal;

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
    private final NameMemory ownerClaims;

    public AtTheDoor(
            AccountRepository accounts,
            Supplier<NamePolicy> names,
            PremiumLookup lookup,
            Supplier<NameOwnership> ownership,
            NameMemory failed,
            NameMemory claims,
            NameMemory ownerClaims) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.names = Objects.requireNonNull(names, "names");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.failed = Objects.requireNonNull(failed, "failed");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.ownerClaims = Objects.requireNonNull(ownerClaims, "ownerClaims");
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
        boolean asked = claimed || ownerClaims.holds(address, name);
        Holder holder = holder(account);
        AtomicReference<PremiumAnswer> answered = new AtomicReference<>();
        Arrival arrival = new Arrival(holder, asked, failed.holds(address, name), host);
        Decision decision = ownership.get().decide(arrival, () -> {
            PremiumAnswer answer = lookup.lookup(name);
            answered.set(answer);
            return answer;
        });
        if (claimed && decision instanceof Decision.Handshake) {
            // A claim buys one try. Passed, the name changes hands; failed, the cracked owner keeps it.
            claims.forget(address, name);
        }
        if (decision instanceof Decision.Refuse refuse && refuse.why() != Refusal.LOOKUP_FAILED) {
            // The failure is explained now, and the next connection is asked again rather than refused again.
            failed.forget(address, name);
        }
        if (holder == Holder.CRACKED_UNVERIFIED && answered.get() instanceof PremiumAnswer.Cracked) {
            account.ifPresent(found -> accounts.update(found.asVerified()));
        }
        return decision;
    }

    /** A player at this address says the name is theirs: their next connection from here is asked. */
    public void claim(String address, PlayerName name) {
        claims.remember(address, name);
    }

    /**
     * The owner of the account, logged in, asks for the handshake: their next connection from here is asked, and
     * its pre-login reads {@link #ownerAsked} to know the account turns premium rather than changes hands.
     */
    public void ownerAsks(String address, PlayerName name) {
        ownerClaims.remember(address, name);
    }

    /** Whether the owner of the account asked for this connection's handshake. Read once, by the pre-login. */
    public boolean ownerAsked(String address, PlayerName name) {
        boolean asked = ownerClaims.holds(address, name);
        ownerClaims.forget(address, name);
        return asked;
    }

    /**
     * A connection from this address under this name failed the handshake. An unverified account under the
     * name is settled: a paid owner exists, its holder is not them, and the first comer keeps it until a claim.
     */
    public void failed(String address, PlayerName name) {
        failed.remember(address, name);
        accounts.findByName(name).filter(Account::unverified).ifPresent(found -> accounts.update(found.asVerified()));
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
