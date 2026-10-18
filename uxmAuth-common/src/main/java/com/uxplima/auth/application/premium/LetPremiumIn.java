package com.uxplima.auth.application.premium;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.Disputes;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.ConflictStrategy;
import com.uxplima.auth.domain.premium.MarkedName;
import com.uxplima.auth.domain.premium.Refusal;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;

/**
 * Lets a connection Mojang proved into the account its Mojang id owns, under the name Mojang gave it.
 *
 * <p>The account is found by the Mojang id first, so a player who renamed at Mojang keeps their account and its
 * UUID. A name another account holds changes hands: a premium account still under a name Mojang has given to
 * somebody else moves away, to come back under its owner's new name when they next join; a cracked account
 * goes through the strategy the operator chose, unless its own owner asked for the handshake, in which case it
 * simply turns premium. This runs on the thread the server gives the pre-login, which may wait on the database.
 */
public final class LetPremiumIn {

    private final AccountRepository accounts;
    private final Disputes disputes;
    private final Supplier<ConflictStrategy> conflict;
    private final Supplier<String> mark;
    private final UuidScheme scheme;
    private final Clock clock;

    public LetPremiumIn(
            AccountRepository accounts,
            Disputes disputes,
            Supplier<ConflictStrategy> conflict,
            Supplier<String> mark,
            UuidScheme scheme,
            Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.disputes = Objects.requireNonNull(disputes, "disputes");
        this.conflict = Objects.requireNonNull(conflict, "conflict");
        this.mark = Objects.requireNonNull(mark, "mark");
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * What a connection Mojang proved under this id and name comes to.
     *
     * @param ownerAsked whether the logged-in owner of a cracked account under the name asked for this handshake
     */
    public PremiumAdmission admit(UUID mojangId, PlayerName name, String address, boolean ownerAsked) {
        Objects.requireNonNull(mojangId, "mojangId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        Optional<Refusal> disputed = disputed(name);
        if (disputed.isPresent()) {
            return new PremiumAdmission.Refused(disputed.get());
        }
        Optional<Account> mine = accounts.findByPremiumId(mojangId);
        Optional<Account> holder = accounts.findByName(name)
                .filter(found -> mine.isEmpty() || !found.id().equals(mine.get().id()));
        if (holder.isPresent()) {
            Optional<Refusal> refused = makeRoom(holder.get(), mojangId, name, mine.isEmpty() && ownerAsked);
            if (refused.isPresent()) {
                return new PremiumAdmission.Refused(refused.get());
            }
        }
        Account account = mine.map(found -> underName(found, name))
                .or(() -> accounts.findByName(name).filter(turned -> turned.premiumId() != null))
                .orElseGet(() -> made(mojangId, name, address));
        Instant now = clock.instant();
        accounts.recordLogin(account.id(), now, address);
        return new PremiumAdmission.In(new AuthState.Authenticated(account.loggedIn(now, address), AuthMethod.PREMIUM));
    }

    /**
     * Frees the name from the account that holds it, or turns that account premium when its owner asked. Empty
     * when the name is free now, or the reason the paid owner is turned away.
     */
    private Optional<Refusal> makeRoom(Account holder, UUID mojangId, PlayerName name, boolean ownerAsked) {
        if (holder.kind() != AccountKind.CRACKED) {
            moveAway(holder);
            return Optional.empty();
        }
        if (ownerAsked) {
            turnPremium(holder, mojangId, name);
            return Optional.empty();
        }
        return switch (conflict.get()) {
            case RENAME -> {
                moveAway(holder);
                yield Optional.empty();
            }
            case BLOCK -> {
                disputes.raise(name, mojangId, clock.instant());
                yield Optional.of(Refusal.NAME_DISPUTED);
            }
            case CRACKED_KEEPS -> Optional.of(Refusal.NAME_KEPT);
            case PREMIUM_TAKES -> {
                accounts.delete(holder.id());
                yield Optional.empty();
            }
        };
    }

    /**
     * Why a dispute under the name turns the paid owner away: staff have not settled it, or they settled it for the
     * cracked account that still holds the name. A settled dispute whose account no longer holds it is gone, and the
     * holder of the moment meets the operator's strategy like any other.
     */
    private Optional<Refusal> disputed(PlayerName name) {
        Optional<Disputes.Dispute> dispute = disputes.of(name);
        if (dispute.isEmpty()) {
            return Optional.empty();
        }
        UUID keptFor = dispute.get().keptFor();
        if (keptFor == null) {
            return Optional.of(Refusal.NAME_DISPUTED);
        }
        if (accounts.findByName(name)
                .filter(held -> held.kind() == AccountKind.CRACKED && held.id().equals(keptFor))
                .isPresent()) {
            return Optional.of(Refusal.NAME_KEPT);
        }
        disputes.resolve(name);
        return Optional.empty();
    }

    /** The account moves to the name and the mark, with everything keyed by its id. */
    private void moveAway(Account holder) {
        PlayerName free = MarkedName.freeFor(
                holder.name(),
                mark.get(),
                candidate -> accounts.findByName(candidate).isPresent());
        if (accounts.rename(holder.id(), free, clock.instant()) instanceof AccountRepository.NameTaken) {
            throw new IllegalStateException("the name " + free.value() + " was taken while "
                    + holder.name().value() + " moved to it");
        }
    }

    /** The owner of a cracked account proved the name: the account turns premium, under the scheme's id. */
    private void turnPremium(Account holder, UUID mojangId, PlayerName name) {
        UUID id = scheme.idFor(name, Optional.of(mojangId), Optional.of(holder));
        if (!id.equals(holder.id())) {
            accounts.moveId(holder.id(), id);
        }
        accounts.update(new Account(
                id,
                name,
                AccountKind.PREMIUM,
                mojangId,
                holder.password(),
                holder.registeredAt(),
                holder.registrationAddress(),
                holder.lastLoginAt(),
                holder.lastAddress()));
    }

    /** The paid player's own account, under the name Mojang gives it now, in Mojang's case. */
    private Account underName(Account account, PlayerName name) {
        if (account.name().value().equals(name.value())) {
            return account;
        }
        if (accounts.rename(account.id(), name, clock.instant()) instanceof AccountRepository.NameTaken taken) {
            throw new IllegalStateException("the name " + taken.stored().value() + " is held although it was freed");
        }
        return accounts.findById(account.id()).orElseThrow();
    }

    private Account made(UUID mojangId, PlayerName name, String address) {
        UUID id = scheme.idFor(name, Optional.of(mojangId), Optional.empty());
        Account fresh =
                new Account(id, name, AccountKind.PREMIUM, mojangId, null, clock.instant(), address, null, null);
        if (accounts.insert(fresh) instanceof AccountRepository.NameTaken) {
            throw new IllegalStateException("the name " + name.value() + " was taken while its paid owner joined");
        }
        return fresh;
    }
}
