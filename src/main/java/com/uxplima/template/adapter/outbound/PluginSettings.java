package com.uxplima.template.adapter.outbound;

import java.nio.file.Path;
import java.util.Objects;

import com.uxplima.uxmlib.config.HoconConfig;

/**
 * The typed view of {@code config.conf}. The file is read once on enable and once per reload, and the
 * result is swapped in as one immutable snapshot. A caller reads {@link #current()} on the hot path, so no
 * caller ever sees a half-applied reload.
 *
 * <p>Nothing in this plugin reads a configuration value by string path outside this class.
 */
public final class PluginSettings {

    /** One immutable snapshot of the file. */
    public record Snapshot(String locale, int exampleLimit) {

        public Snapshot {
            Objects.requireNonNull(locale, "locale");
            if (exampleLimit < 1) {
                throw new IllegalArgumentException("example-limit must be at least 1, got: " + exampleLimit);
            }
        }
    }

    private final Path file;

    // A volatile reference, not an AtomicReference. A reference assignment is already atomic, and volatile
    // gives the visibility that a reader on another region thread needs. AtomicReference would only add a
    // compare-and-set that nothing here uses, and its get() is nullable, which this field never is.
    private volatile Snapshot current;

    public PluginSettings(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.current = read();
    }

    public Snapshot current() {
        return current;
    }

    /**
     * Re-read the file. A parse error propagates, and the previous snapshot stays in place, so the server
     * keeps running on the last configuration that was known to be good.
     */
    public void reload() {
        this.current = read();
    }

    private Snapshot read() {
        HoconConfig config = HoconConfig.load(file);
        return new Snapshot(config.getString("locale", "en"), config.getInt("example.limit", 3));
    }
}
