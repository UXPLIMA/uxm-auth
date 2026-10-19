package com.uxplima.auth.application.migrate;

import java.util.Objects;

/**
 * What reading a premium list did, in counts.
 *
 * @param marked cracked accounts turned premium
 * @param already accounts that were not cracked, left as they were
 * @param unknownNames names no account holds, never created
 * @param taken premium ids another account holds already
 * @param notOwner names that shared a premium id Mojang gives another name
 * @param unanswered names that shared a premium id no source could place, left as they were
 * @param refusedNames names this server's rules refuse
 */
public record PremiumReport(
        String key,
        boolean alreadyDone,
        int marked,
        int already,
        int unknownNames,
        int taken,
        int notOwner,
        int unanswered,
        int refusedNames) {

    public PremiumReport {
        Objects.requireNonNull(key, "key");
    }

    /** The report of a list read before, which did nothing now. */
    static PremiumReport done(String key) {
        return new PremiumReport(key, true, 0, 0, 0, 0, 0, 0, 0);
    }
}
