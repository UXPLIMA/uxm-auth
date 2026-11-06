package com.uxplima.auth.application.port;

import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.uxplima.auth.domain.protection.AddressTally;

/**
 * The tally of wrong passwords and codes typed for each account, from any address, kept where every member of a
 * network reads it. It is kept in the shape an address's tally is, a lock standing where a ban would.
 */
public interface AccountTallies {

    /** What was typed wrong for the account so far, nothing for an account never tried wrong. */
    Optional<AddressTally> find(UUID account);

    /**
     * Changes the tally of the account in one step against every other writer, starting from a clean tally the
     * first time, and answers what it became.
     */
    AddressTally change(UUID account, UnaryOperator<AddressTally> change);

    /** Forgets the account's tally: its count and its lock. */
    void clear(UUID account);
}
