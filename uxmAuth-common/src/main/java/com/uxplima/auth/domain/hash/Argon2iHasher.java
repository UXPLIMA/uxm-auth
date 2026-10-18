package com.uxplima.auth.domain.hash;

import com.uxplima.auth.domain.password.Password;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Argon2i, the variant a website's login table holds when it is set to argon2:
 * {@code $argon2i$v=19$m=<KiB>,t=<iterations>,p=<lanes>$<salt>$<hash>}. Written only for that table; the plugin's
 * own choice is argon2id, which resists the attacks argon2i does not.
 */
public final class Argon2iHasher implements PasswordHasher {

    private final Argon2Phc phc;

    public Argon2iHasher(int memoryKib, int iterations, int parallelism) {
        this.phc = new Argon2Phc("argon2i", Argon2Parameters.ARGON2_i, memoryKib, iterations, parallelism);
    }

    @Override
    public HashAlgorithm algorithm() {
        return HashAlgorithm.ARGON2I;
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
