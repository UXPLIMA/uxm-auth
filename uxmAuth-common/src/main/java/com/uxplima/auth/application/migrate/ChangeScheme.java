package com.uxplima.auth.application.migrate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;

import com.uxplima.auth.application.port.AccountList;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.SchemeRecord;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.premium.UuidScheme;

/**
 * Gives every account the id another UUID scheme gives it, and then writes that scheme down.
 *
 * <p>Each account moves by {@link AccountRepository#moveId}, which carries everything kept under the id. An id
 * another account holds is waited for: once that account has moved away, the id is free. Two accounts that would
 * take one id, or trade ids, are named and left as they are. The scheme is written last, so a run that stops
 * halfway is run again, and the accounts it moved already are found where they belong.
 */
public final class ChangeScheme {

    /** What a change came to. */
    public sealed interface Changed {

        /** The accounts that moved, those already where the scheme puts them, and the names left. */
        record Done(int moved, int unchanged, List<String> left) implements Changed {
            public Done {
                left = List.copyOf(left);
            }
        }

        /** A player is online, and nothing moved. */
        record PlayersOnline() implements Changed {}
    }

    private final AccountList list;
    private final AccountRepository accounts;
    private final SchemeRecord record;
    private final IntSupplier online;

    /** @param online how many players are on the server now */
    public ChangeScheme(AccountList list, AccountRepository accounts, SchemeRecord record, IntSupplier online) {
        this.list = Objects.requireNonNull(list, "list");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.record = Objects.requireNonNull(record, "record");
        this.online = Objects.requireNonNull(online, "online");
    }

    /** Moves every account to the scheme, or says why not. It writes the table: off any server thread. */
    public Changed change(UuidScheme to) {
        Objects.requireNonNull(to, "to");
        if (online.getAsInt() > 0) {
            return new Changed.PlayersOnline();
        }
        List<Account> all = new ArrayList<>(list.all());
        all.sort(Comparator.comparing(account -> account.name().key()));
        Map<UUID, UUID> targets = new HashMap<>();
        all.forEach(account -> targets.put(account.id(), targetOf(account, to)));
        List<Account> left = shared(all, targets);
        List<Account> pending = new ArrayList<>(all.stream()
                .filter(account -> !account.id().equals(targets.get(account.id())) && !left.contains(account))
                .toList());
        int moved = moveFree(pending, targets, all);
        left.addAll(pending);
        record.replace(to);
        List<String> names =
                left.stream().map(account -> account.name().value()).sorted().toList();
        return new Changed.Done(moved, all.size() - moved - left.size(), names);
    }

    /**
     * The id the scheme gives the account. A Bedrock account keeps the id Floodgate gives it, and keep-first and
     * random keep the id each account holds, so only offline and mojang move anything.
     */
    private static UUID targetOf(Account account, UuidScheme to) {
        if (account.kind() == AccountKind.BEDROCK || to == UuidScheme.KEEP_FIRST || to == UuidScheme.RANDOM) {
            return account.id();
        }
        Optional<UUID> paid =
                account.kind() == AccountKind.PREMIUM ? Optional.ofNullable(account.premiumId()) : Optional.empty();
        return to.idFor(account.name(), paid, Optional.empty());
    }

    /** The accounts whose new id another account's new id is too: none of them can have it. */
    private static List<Account> shared(List<Account> all, Map<UUID, UUID> targets) {
        Map<UUID, Integer> takers = new HashMap<>();
        targets.values().forEach(target -> takers.merge(target, 1, Integer::sum));
        return new ArrayList<>(all.stream()
                .filter(account -> takers.getOrDefault(targets.get(account.id()), 0) > 1)
                .toList());
    }

    /** Moves each pending account whose new id nobody holds, pass after pass, until a pass moves none. */
    private int moveFree(List<Account> pending, Map<UUID, UUID> targets, List<Account> all) {
        Set<UUID> held = new HashSet<>();
        all.forEach(account -> held.add(account.id()));
        int moved = 0;
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Account account : List.copyOf(pending)) {
                UUID target = Objects.requireNonNull(targets.get(account.id()));
                if (!held.contains(target)) {
                    accounts.moveId(account.id(), target);
                    held.remove(account.id());
                    held.add(target);
                    pending.remove(account);
                    moved++;
                    progress = true;
                }
            }
        }
        return moved;
    }
}
