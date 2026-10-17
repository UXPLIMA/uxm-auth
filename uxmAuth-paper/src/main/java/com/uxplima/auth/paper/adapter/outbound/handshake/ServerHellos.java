package com.uxplima.auth.paper.adapter.outbound.handshake;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import com.uxplima.auth.handshake.Handshake;
import com.uxplima.auth.handshake.HandshakeResults;
import com.uxplima.auth.handshake.HelloDoor;
import com.uxplima.auth.handshake.HelloOutcome;
import com.uxplima.auth.paper.adapter.inbound.listener.Hellos;

/**
 * The handshake on this server, when it can run here. Where it cannot, the console says why once and every
 * connection logs in with a password as before.
 *
 * <p>A server that is not the one the handshake module was built against fails to link its classes; that is
 * read as "cannot run here" too, never as a failed start.
 */
public final class ServerHellos implements Hellos, AutoCloseable {

    private final HandshakeResults results = new HandshakeResults();
    private final boolean installed;

    private ServerHellos(boolean installed) {
        this.installed = installed;
    }

    /** No handshake, as before one is started. */
    public static ServerHellos none() {
        return new ServerHellos(false);
    }

    /** Installs the handshake if this server can run it, and says in the console why not if it cannot. */
    public static ServerHellos startOn(HelloDoor door, Logger logger) {
        Objects.requireNonNull(door, "door");
        Objects.requireNonNull(logger, "logger");
        ServerHellos hellos;
        try {
            Optional<String> why = Handshake.whyNot();
            if (why.isPresent()) {
                logger.warning("Premium auto-login is off: " + why.get());
                return new ServerHellos(false);
            }
            hellos = new ServerHellos(true);
            Handshake.install(door, hellos.results, logger);
        } catch (LinkageError notThisServer) {
            logger.warning("Premium auto-login is off: this server is not the Paper this uxmAuth was built for ("
                    + notThisServer.getMessage() + ")");
            return new ServerHellos(false);
        }
        logger.info("Premium auto-login is on: paid accounts are proved by Mojang at the door.");
        return hellos;
    }

    @Override
    public boolean installed() {
        return installed;
    }

    @Override
    public Optional<HelloOutcome> take(InetSocketAddress address) {
        return results.take(Objects.requireNonNull(address, "address"));
    }

    @Override
    public void close() {
        if (installed) {
            Handshake.uninstall();
        }
    }
}
