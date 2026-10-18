package com.uxplima.auth.application.staff;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.Disputes;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.premium.MarkedName;

/**
 * Staff settle a name a cracked holder and a paid owner both want, which the block strategy holds for them. For the
 * paid owner, the holder moves to the name and the mark, as the rename strategy moves them, and the owner gets the
 * name at their next join. For the holder, they keep it, and the owner is told to change theirs.
 */
public final class ResolveConflict {

    /** Who gets the name. */
    public enum Side {
        PREMIUM,
        CRACKED
    }

    /** What settling came to. */
    public enum Resolved {
        /** The paid owner gets the name; the holder moved. */
        PREMIUM_TAKES,
        /** The cracked holder keeps the name. */
        CRACKED_KEEPS,
        /** The name is in no dispute. */
        NO_DISPUTE
    }

    private final AccountRepository accounts;
    private final Disputes disputes;
    private final Supplier<String> mark;
    private final NetworkEvents events;
    private final Clock clock;

    /** @param mark what the holder's new name carries, as ownership.rename-mark in config.conf writes it */
    public ResolveConflict(
            AccountRepository accounts, Disputes disputes, Supplier<String> mark, NetworkEvents events, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.disputes = Objects.requireNonNull(disputes, "disputes");
        this.mark = Objects.requireNonNull(mark, "mark");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Settles the dispute under this name for one side. Writes the tables. */
    public Resolved resolve(PlayerName name, Side side) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(side, "side");
        if (disputes.of(name).isEmpty()) {
            return Resolved.NO_DISPUTE;
        }
        if (side == Side.CRACKED) {
            Optional<Account> holder = accounts.findByName(name);
            if (holder.isEmpty()) {
                // Nobody holds the name any more, so nothing is kept: the paid owner gets it at their next join.
                disputes.resolve(name);
                return Resolved.PREMIUM_TAKES;
            }
            disputes.keep(name, holder.get().id());
            return Resolved.CRACKED_KEEPS;
        }
        accounts.findByName(name).ifPresent(this::moveAway);
        disputes.resolve(name);
        return Resolved.PREMIUM_TAKES;
    }

    /** The holder moves to the name and the mark, with everything keyed by its id, and joins again under it. */
    private void moveAway(Account holder) {
        PlayerName free = MarkedName.freeFor(
                holder.name(),
                mark.get(),
                candidate -> accounts.findByName(candidate).isPresent());
        if (accounts.rename(holder.id(), free, clock.instant()) instanceof AccountRepository.NameTaken) {
            throw new IllegalStateException("the name " + free.value() + " was taken while "
                    + holder.name().value() + " moved to it");
        }
        events.publish(new Bulletin.Rejoin(holder.id()));
    }
}
