package com.uxplima.auth.application.port;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.PasswordHash;

/** Each account's second factors, shared by a whole network: the app secret and the backup codes. */
public interface Factors {

    /**
     * An account's app secret and the latest step a code was taken for.
     *
     * @param lastStep the step of the latest code taken, or -1 before any
     */
    record App(TotpSecret secret, long lastStep) {
        public App {
            Objects.requireNonNull(secret, "secret");
        }
    }

    /** The account's app, when it has one. */
    Optional<App> app(UUID account);

    /** Keep this secret for the account, in place of any before it, with no step taken yet. */
    void setApp(UUID account, TotpSecret secret);

    /** The account no longer has an app. */
    void removeApp(UUID account);

    /**
     * Takes a step for the account, only past the latest one taken, once across every server that shares the
     * table; says whether it was taken.
     */
    boolean useStep(UUID account, long step);

    /** The hashes of the account's unused backup codes. */
    List<PasswordHash> backupCodes(UUID account);

    /** Keep these codes for the account, in place of every code before them. */
    void setBackupCodes(UUID account, List<PasswordHash> codes);

    /** Spends one code of the account, once; says whether it was there to spend. */
    boolean useBackupCode(UUID account, PasswordHash code);
}
