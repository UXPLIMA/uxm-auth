package com.uxplima.auth.domain.hash;

import com.uxplima.auth.domain.password.Password;

/** One hash algorithm: it writes a hash and checks a password against one it recognises. */
public interface PasswordHasher {

    HashAlgorithm algorithm();

    /** A fresh hash of this password, with a new random salt. */
    PasswordHash hash(Password password);

    /** Whether this password is the one behind this hash. False for a hash this algorithm cannot read. */
    boolean verify(Password password, PasswordHash hash);

    /** Whether this hash was written with this hasher's current parameters, so it needs no rehash. */
    boolean isCurrent(PasswordHash hash);
}
