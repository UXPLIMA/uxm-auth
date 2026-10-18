package com.uxplima.auth.application.port;

import com.uxplima.auth.domain.password.Password;

/** Whether a password is known from a breach elsewhere. One that cannot be asked is let through. */
@FunctionalInterface
public interface BreachedPasswords {

    /** Nothing is known of any password. */
    BreachedPasswords NONE = password -> false;

    /** Whether the password is known from a breach; it blocks, so it is asked on the hashing lane. */
    boolean breached(Password password);
}
