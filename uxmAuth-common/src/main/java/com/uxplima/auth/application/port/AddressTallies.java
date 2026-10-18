package com.uxplima.auth.application.port;

import java.util.Optional;
import java.util.function.UnaryOperator;

import com.uxplima.auth.domain.protection.AddressTally;

/** The tally of each address key, kept where every member of a network reads it. */
public interface AddressTallies {

    /** What the address did wrong so far, nothing for an address never seen. */
    Optional<AddressTally> find(String key);

    /**
     * Changes the tally of the address in one step against every other writer, starting from a clean tally the
     * first time, and answers what it became.
     */
    AddressTally change(String key, UnaryOperator<AddressTally> change);

    /** Forgets the address: its failures and its ban. */
    void clear(String key);
}
