package com.uxplima.auth.velocity.adapter.outbound.limbo;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

/**
 * What this plugin asks of one generation of LimboAPI. LimboAPI 2 moved the world, chunk, file and player types
 * into new packages, so a class that names them links against one generation only. Each generation gets its own
 * implementation, compiled against its own release, and {@link LimboKits} loads the one the proxy carries.
 */
public interface LimboKit {

    /**
     * Build the waiting world: the operator's file where it was saved, or a small platform under the spawn when there
     * is none, answering the given commands.
     *
     * @param factory the LimboAPI plugin, as the proxy hands it out
     */
    Room open(
            Object factory,
            Path folder,
            AuthSettings.Network settings,
            Collection<CommandMeta> commands,
            Logger logger);

    /** The built world. */
    interface Room {

        /** Put the player in the world; {@code guest} hears what happens to them there. */
        void spawn(Player player, Guest guest);
    }

    /** One player's time in the world. LimboAPI calls it on the player's own connection thread. */
    interface Guest {

        void arrive(Inside inside);

        void said(String line);

        void left();
    }

    /** What the world does for a player standing in it. */
    interface Inside {

        void stopFalling();

        /** The player's own connection thread, where every change to them in the world runs. */
        ScheduledExecutorService loop();

        /** Let the player out of the world, on to the server the proxy picks. */
        void leave();

        /** Put the picture on a map in the player's hand. */
        void draw(BufferedImage picture);
    }
}
