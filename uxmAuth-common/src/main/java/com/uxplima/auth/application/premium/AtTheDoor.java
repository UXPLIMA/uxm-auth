package com.uxplima.auth.application.premium;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.Disputes;
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
import com.uxplima.auth.domain.protection.SharedAddresses;

/**
 * A connection at the hello, before the server makes a profile for it: is it asked for the handshake?
 *
 * <p>The account is read first, and the web only where the operator's rule needs it. A name the door will
 * refuse anyway for its spelling is never asked about: the name check at the pre-login says why, and the web and
 * Mojang's cap are not spent on it. A name registered here in another case is asked about once it arrives in a
 * spelling of its own, because that may be its paid owner in Mojang's spelling, whom the pre-login would
 * otherwise send back to the holder's spelling forever. A name the web has not answered lately costs the
 * address that brought it one lookup of its budget; past that the name counts as one nobody could look up. A
 * shared-address filter is everybody's address, so no budget holds it, no claim is taken from it, and a failure
 * remembered for it turns nobody away. This may wait on the database and the network, so it never runs on a
 * thread the server needs.
 */
public final class AtTheDoor {

    private final AccountRepository accounts;
    private final Supplier<NamePolicy> names;
    private final PremiumLookup lookup;
    private final Supplier<NameOwnership> ownership;
    private final NameMemory failed;
    private final NameMemory claims;
    private final NameMemory ownerClaims;
    private final Disputes disputes;
    private final Supplier<SharedAddresses> shared;
    private final LookupBudget budget;

    /** No shared-address filter, and no budget for an address. */
    public AtTheDoor(
            AccountRepository accounts,
            Supplier<NamePolicy> names,
            PremiumLookup lookup,
            Supplier<NameOwnership> ownership,
            NameMemory failed,
            NameMemory claims,
            NameMemory ownerClaims,
            Disputes disputes) {
        this(
                accounts,
                names,
                lookup,
                ownership,
                failed,
                claims,
                ownerClaims,
                disputes,
                SharedAddresses::none,
                LookupBudget.unlimited());
    }

    /**
     * @param shared the filters every player of which arrives from the filter's own address
     * @param budget how many names one address may have the web asked about
     */
    public AtTheDoor(
            AccountRepository accounts,
            Supplier<NamePolicy> names,
            PremiumLookup lookup,
            Supplier<NameOwnership> ownership,
            NameMemory failed,
            NameMemory claims,
            NameMemory ownerClaims,
            Disputes disputes,
            Supplier<SharedAddresses> shared,
            LookupBudget budget) {
        this.shared = Objects.requireNonNull(shared, "shared");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.names = Objects.requireNonNull(names, "names");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.failed = Objects.requireNonNull(failed, "failed");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.ownerClaims = Objects.requireNonNull(ownerClaims, "ownerClaims");
        this.disputes = Objects.requireNonNull(disputes, "disputes");
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
        if (disputes.of(name).filter(dispute -> !dispute.kept()).isPresent()) {
            // Both sides wait for staff: the paid owner and the cracked holder alike.
            return new Decision.Refuse(Refusal.NAME_DISPUTED);
        }
        Optional<Account> account = accounts.findByName(name);
        boolean otherCase = account.isPresent() && !account.get().name().value().equals(raw);
        boolean throughAFilter = shared.get().through(address, host);
        boolean claimed = !throughAFilter && claims.holds(address, name);
        boolean asked = claimed || (!throughAFilter && ownerClaims.holds(address, name));
        Holder holder = holder(account);
        AtomicReference<PremiumAnswer> answered = new AtomicReference<>();
        Arrival arrival = new Arrival(raw, holder, otherCase, asked, failed.holds(address, name), throughAFilter, host);
        Decision decision = ownership.get().decide(arrival, () -> {
            PremiumAnswer answer = lookup.known(name)
                    .orElseGet(() ->
                            throughAFilter || budget.take(address) ? lookup.lookup(name) : new PremiumAnswer.Unknown());
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
        if (holder == Holder.CRACKED_UNVERIFIED
                && decision instanceof Decision.Cracked
                && answered.get() != null
                && !(answered.get() instanceof PremiumAnswer.Unknown)) {
            // A source answered and the account goes on with its password: who owns the name is settled.
            account.ifPresent(found -> accounts.update(found.asVerified()));
        }
        return decision;
    }

    /**
     * A player at this address says the name is theirs: their next connection from here is asked. Not through a
     * shared-address filter, whose next connection may be anybody's: then nothing is remembered, and the answer
     * is false.
     */
    public boolean claim(String address, PlayerName name) {
        if (sharedAddress(address)) {
            return false;
        }
        claims.remember(address, name);
        return true;
    }

    /**
     * The owner of the account, logged in, asks for the handshake: their next connection from here is asked, and
     * its pre-login reads {@link #ownerAsked} to know the account turns premium rather than changes hands. Not
     * through a shared-address filter, where the next proved connection may be another player's, who would be
     * handed the account: then nothing is remembered, and the answer is false.
     */
    public boolean ownerAsks(String address, PlayerName name) {
        if (sharedAddress(address)) {
            return false;
        }
        ownerClaims.remember(address, name);
        return true;
    }

    /** Whether the owner of the account asked for this connection's handshake. Read once, by the pre-login. */
    public boolean ownerAsked(String address, PlayerName name) {
        boolean asked = ownerClaims.holds(address, name) && !sharedAddress(address);
        ownerClaims.forget(address, name);
        return asked;
    }

    /**
     * Whether this is a shared-address filter's address, whatever host was typed: a claim kept under it would be
     * every player's behind the filter.
     */
    private boolean sharedAddress(String address) {
        return shared.get().through(address, "");
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
            case PREMIUM -> account.get().password() == null ? Holder.PREMIUM : Holder.PREMIUM_WITH_PASSWORD;
            case BEDROCK -> Holder.BEDROCK;
            case CRACKED -> account.get().unverified() ? Holder.CRACKED_UNVERIFIED : Holder.CRACKED;
        };
    }
}
