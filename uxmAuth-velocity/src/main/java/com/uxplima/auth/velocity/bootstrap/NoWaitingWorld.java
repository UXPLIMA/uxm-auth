package com.uxplima.auth.velocity.bootstrap;

import org.jspecify.annotations.Nullable;

/**
 * config.conf asks for the proxy's own waiting world and the proxy cannot build it. The start stops here: a player
 * would otherwise wait on a server, and a server without uxmAuth of its own lets them straight in.
 */
final class NoWaitingWorld extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    NoWaitingWorld(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
