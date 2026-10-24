package com.uxplima.auth.velocity.adapter.outbound.limbo;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Optional;

/**
 * Finds the kit for the LimboAPI on this proxy by the place one of its types lives. The kits are loaded by name,
 * because each is compiled against its own LimboAPI and only the one the proxy carries can link.
 */
public final class LimboKits {

    /** LimboAPI 2 keeps its chunk types under world. */
    static final String TWO_MARK = "net.elytrium.limboapi.api.world.chunk.Dimension";

    /** LimboAPI 1.1, which runs on Velocity 3 and 4, keeps them under chunk. */
    static final String ONE_MARK = "net.elytrium.limboapi.api.chunk.Dimension";

    static final String TWO = "com.uxplima.auth.velocity.adapter.outbound.limbo.two.LimboTwo";
    static final String ONE = "com.uxplima.auth.velocity.adapter.outbound.limbo.one.LimboOne";

    private LimboKits() {}

    /**
     * The kit that speaks to this LimboAPI, or empty when it is a generation this plugin does not know.
     *
     * @param factory the LimboAPI plugin, as the proxy hands it out
     */
    public static Optional<LimboKit> of(Object factory) {
        ClassLoader limbo =
                Objects.requireNonNull(factory, "factory").getClass().getClassLoader();
        if (limbo == null) {
            return Optional.empty();
        }
        if (present(limbo, TWO_MARK)) {
            return Optional.of(load(TWO));
        }
        if (present(limbo, ONE_MARK)) {
            return Optional.of(load(ONE));
        }
        return Optional.empty();
    }

    private static boolean present(ClassLoader loader, String name) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    private static LimboKit load(String kit) {
        try {
            return Class.forName(kit, true, LimboKits.class.getClassLoader())
                    .asSubclass(LimboKit.class)
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | InstantiationException
                | IllegalAccessException
                | InvocationTargetException missing) {
            throw new IllegalStateException("The jar is missing " + kit + ".", missing);
        }
    }
}
