package com.uxplima.auth.application.port;

import java.time.Instant;
import java.util.Optional;

/** Which imports ran to the end, so an import runs once. */
public interface ImportMarks {

    /** When the import of this source completed, if it did. */
    Optional<Instant> completed(String key);

    /** The import of this source completed now. */
    void complete(String key, Instant at);
}
