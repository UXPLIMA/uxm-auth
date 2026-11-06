package com.uxplima.auth.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Which imports ran to the end, so an import runs once, and which accounts each one wrote, so a run that failed
 * halfway writes again over its own accounts and never over one this plugin held before.
 */
public interface ImportMarks {

    /** When the import of this source completed, if it did. */
    Optional<Instant> completed(String key);

    /** The import of this source completed now. */
    void complete(String key, Instant at);

    /** Whether the import of this source made this account, in this run or one that did not complete. */
    boolean wrote(String key, UUID account);

    /** The import of this source made this account. */
    void written(String key, UUID account);
}
