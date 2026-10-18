package com.uxplima.auth.domain.hash;

import com.uxplima.auth.domain.password.Password;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Argon2id in the PHC string form every Argon2 library writes:
 * {@code $argon2id$v=19$m=<KiB>,t=<iterations>,p=<lanes>$<salt>$<hash>}, base64 without padding.
 *
 * <p>A stored hash is verified with the parameters written in it, so a hash another plugin wrote with other numbers
 * still verifies, and {@link #isCurrent} tells the caller to write it again with ours.
 */
public final class Argon2idHasher implements PasswordHasher {

    private final Argon2Phc phc;

    public Argon2idHasher(int memoryKib, int iterations, int parallelism) {
        this.phc = new Argon2Phc("argon2id", Argon2Parameters.ARGON2_id, memoryKib, iterations, parallelism);
    }

    @Override
    public HashAlgorithm algorithm() {
        return HashAlgorithm.ARGON2ID;
    }

    @Override
    public PasswordHash hash(Password password) {
        return phc.hash(password);
    }

    @Override
    public boolean verify(Password password, PasswordHash hash) {
        return phc.verify(password, hash);
    }

    @Override
    public boolean isCurrent(PasswordHash hash) {
        return phc.isCurrent(hash);
    }
}
