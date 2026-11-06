package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.logging.Logger;

import net.kyori.adventure.audience.Audience;

/**
 * A press the answers would otherwise leave without a reply. Every screen of this plugin is opened to wait for the
 * server's response, so a client whose press gets nothing back sits on "Waiting for Server" until the login runs
 * out of time. Each such press is written to the log and answered here.
 */
public final class UnansweredPresses {

    /** Why a press from a connection still connecting was not run. */
    public enum Dropped {
        /** No connection waits at the dialog under this id: it was let in, sent away, or never held. */
        NOT_WAITING,
        /** A connection waits under this id, but not the one that pressed. */
        OTHER_CONNECTION
    }

    private final ScreenShower shower;
    private final Logger logger;

    public UnansweredPresses(ScreenShower shower, Logger logger) {
        this.shower = Objects.requireNonNull(shower, "shower");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * A press the gate did not run. The screen is taken away rather than shown again: a connection that already
     * went in would otherwise be handed a password screen on its way to the world.
     *
     * @param opening the session generation the waiting connection opened its screen under, when one waits
     */
    public void dropped(Audience who, String name, OptionalLong opening, Dropped why) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(opening, "opening");
        Objects.requireNonNull(why, "why");
        logger.info(() -> "A screen press from " + name + " was not run (" + why + ", opening " + describe(opening)
                + "); the screen is closed.");
        shower.close(who);
    }

    /**
     * A login whose session is gone or was opened again since the screen was shown. The screen is shown again, so
     * the player sees the door answer and can try once more; the gate's own clock still bounds the wait.
     */
    public void gone(Audience who, String name, OptionalLong opening, DialogSpec screen) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(opening, "opening");
        Objects.requireNonNull(screen, "screen");
        logger.info(() -> "A login from " + name + " found its session gone or opened again (opening "
                + describe(opening) + "); the login screen is shown again.");
        shower.show(who, screen);
    }

    /** Which press the gate cannot run, or nothing for one it runs. */
    public static Optional<Dropped> dropFor(boolean waiting, boolean sameConnection) {
        if (!waiting) {
            return Optional.of(Dropped.NOT_WAITING);
        }
        return sameConnection ? Optional.empty() : Optional.of(Dropped.OTHER_CONNECTION);
    }

    private static String describe(OptionalLong opening) {
        return opening.isPresent() ? String.valueOf(opening.getAsLong()) : "none";
    }
}
