package com.uxplima.auth.velocity.adapter.outbound.limbo;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The kinds of world file LimboAPI reads. Every generation names its own type for them with these same constants, so
 * a kit finds its own by name.
 */
public enum WorldFileKind {
    WORLDEDIT_SCHEM,
    SCHEMATIC,
    STRUCTURE;

    /** The kind of world file this name is, by its extension. */
    public static Optional<WorldFileKind> of(String file) {
        String name = Objects.requireNonNull(file, "file").toLowerCase(Locale.ROOT);
        if (name.endsWith(".schem")) {
            return Optional.of(WORLDEDIT_SCHEM);
        }
        if (name.endsWith(".schematic")) {
            return Optional.of(SCHEMATIC);
        }
        if (name.endsWith(".nbt")) {
            return Optional.of(STRUCTURE);
        }
        return Optional.empty();
    }
}
