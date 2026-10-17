package com.uxplima.auth.paper.adapter.outbound.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

/**
 * Whether a client can draw a dialog. Dialogs came with 1.21.6. A server speaks to its own version natively,
 * and only ViaVersion lets an older client in, so ViaVersion is the only one asked.
 *
 * <p>ViaVersion is an optional link: it is reached through its own class loader by name, so a server without it
 * loads none of its classes and every client counts as the server's own version.
 */
public final class DialogClients implements Predicate<UUID> {

    /** What a version reader answers for a client it does not know. */
    public static final int UNKNOWN = -1;

    /** The protocol number of 1.21.6, the first release that draws a dialog. */
    static final int FIRST_WITH_DIALOGS = 771;

    private static final String VIA_PLUGIN = "ViaVersion";
    private static final String VIA_API = "com.viaversion.viaversion.api.Via";

    private final ToIntFunction<UUID> versions;

    DialogClients(ToIntFunction<UUID> versions) {
        this.versions = Objects.requireNonNull(versions, "versions");
    }

    /** The answer for this server: ViaVersion's when it runs, otherwise every client can. */
    public static Predicate<UUID> on(Server server, Logger logger) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(logger, "logger");
        Plugin via = server.getPluginManager().getPlugin(VIA_PLUGIN);
        if (via == null || !via.isEnabled()) {
            return id -> true;
        }
        return new DialogClients(new ViaVersions(via.getClass().getClassLoader(), logger));
    }

    @Override
    public boolean test(UUID connection) {
        int version = versions.applyAsInt(Objects.requireNonNull(connection, "connection"));
        return version == UNKNOWN || version >= FIRST_WITH_DIALOGS;
    }

    /** {@code Via.getAPI().getPlayerVersion(uuid)}, by name; anything that goes wrong reads as unknown. */
    private static final class ViaVersions implements ToIntFunction<UUID> {

        private final ClassLoader loader;
        private final Logger logger;

        ViaVersions(ClassLoader loader, Logger logger) {
            this.loader = loader;
            this.logger = logger;
        }

        @Override
        public int applyAsInt(UUID connection) {
            try {
                Object api =
                        Class.forName(VIA_API, true, loader).getMethod("getAPI").invoke(null);
                Method version = api.getClass().getMethod("getPlayerVersion", UUID.class);
                return version.invoke(api, connection) instanceof Integer number ? number : UNKNOWN;
            } catch (ReflectiveOperationException | RuntimeException failed) {
                Throwable cause = failed instanceof InvocationTargetException wrapped && wrapped.getCause() != null
                        ? wrapped.getCause()
                        : failed;
                logger.log(Level.FINE, "ViaVersion could not tell a client's version", cause);
                return UNKNOWN;
            }
        }
    }
}
