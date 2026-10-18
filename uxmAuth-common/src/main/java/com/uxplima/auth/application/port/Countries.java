package com.uxplima.auth.application.port;

import java.util.Optional;

/** Where an address is, as the country file says. */
public interface Countries {

    /** Whether a country file is open to ask. */
    boolean ready();

    /** The two-letter code of the country the address is in, in upper case, or nothing when it is not known. */
    Optional<String> of(String address);
}
