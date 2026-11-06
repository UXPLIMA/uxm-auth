package com.uxplima.auth.application.migrate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What an import did, in counts. It names the names it refused, which are not secrets, and the formats of the
 * hashes it could not read, never a hash.
 *
 * @param read every row the source held
 * @param held the accounts left as they were, because this plugin held their name or id before the import
 * @param refusedNames the names this server's rules refused, the first {@link #NAMED} of them
 * @param refusedNameCount every name refused
 * @param unknownHashes how many hashes of each unread format
 */
public record ImportReport(
        String key,
        boolean alreadyDone,
        int read,
        int imported,
        int updated,
        int duplicates,
        int held,
        int refusedSecrets,
        List<String> refusedNames,
        int refusedNameCount,
        Map<String, Integer> unknownHashes) {

    /** How many refused names a report lists; the rest are counted. */
    public static final int NAMED = 20;

    public ImportReport {
        Objects.requireNonNull(key, "key");
        refusedNames = List.copyOf(refusedNames);
        unknownHashes = Map.copyOf(unknownHashes);
    }

    /** The report of an import that had run before, and did nothing now. */
    static ImportReport done(String key) {
        return new ImportReport(key, true, 0, 0, 0, 0, 0, 0, List.of(), 0, Map.of());
    }

    /** Whether the source held no row at all, so nothing was marked complete and the next start reads it again. */
    public boolean empty() {
        return !alreadyDone && read == 0;
    }
}
