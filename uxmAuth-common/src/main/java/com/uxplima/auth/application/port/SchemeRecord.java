package com.uxplima.auth.application.port;

import java.util.Optional;

import com.uxplima.auth.domain.premium.UuidScheme;

/** The UUID scheme the accounts are under, written beside them. */
public interface SchemeRecord {

    /** The scheme written down, if a start has written one. */
    Optional<UuidScheme> recorded();

    /** Writes this scheme down in place of the one there. */
    void replace(UuidScheme scheme);
}
