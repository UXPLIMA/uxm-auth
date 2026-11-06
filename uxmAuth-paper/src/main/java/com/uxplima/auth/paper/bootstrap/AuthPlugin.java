package com.uxplima.auth.paper.bootstrap;

import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.ImportFailed;
import com.uxplima.auth.adapter.outbound.SchemeChanged;
import com.uxplima.auth.paper.adapter.inbound.listener.LockedDoor;
import org.bstats.bukkit.Metrics;
import org.jspecify.annotations.Nullable;

/**
 * The plugin shell. It owns the Bukkit lifecycle and nothing else: the graph is built in
 * {@link PluginModule}, so this class stays small enough to read at a glance.
 *
 * <p>It is not final, because the mock server loads a plugin by subclassing it, and the test that enables the
 * whole plugin loads this one. Nothing of the shell is written for a subclass.
 */
public class AuthPlugin extends JavaPlugin {

    /**
     * The bStats id this plugin reports under.
     *
     * <p>Anonymous counts only: how many servers run it, on what platform and in what version. An operator
     * turns the whole of it off in {@code plugins/bStats/config.yml}, which is one switch for every bStats
     * plugin on the server rather than one of ours to invent.
     *
     * <p>Zero while this plugin has no id of its own. Nothing is sent then: reporting under somebody else's
     * number is worse than reporting nothing.
     */
    private static final int METRICS_ID = 0;

    private @Nullable PluginModule module;

    @Override
    public void onEnable() {
        PluginModule wired = null;
        try {
            wired = new PluginModule(this);
            wired.wire();
        } catch (SchemeChanged | ImportFailed refused) {
            // An operator's mistake with a sentence that fixes it, not a defect: no stack trace to read past.
            getLogger().severe(refused.getMessage());
            lock(wired);
            return;
        } catch (Throwable failed) {
            // An Error as much as an exception: a class that does not load or link leaves no login running either.
            // Nothing is thrown on, an out-of-memory error included, because Paper disables a plugin whose enable
            // throws, and that takes the locked door away with it.
            getLogger().log(Level.SEVERE, "uxmAuth could not start: " + failed.getMessage(), failed);
            lock(wired);
            return;
        }
        module = wired;
        try {
            startMetrics();
        } catch (RuntimeException uncounted) {
            getLogger().log(Level.WARNING, "The anonymous counts could not start", uncounted);
        }
    }

    @Override
    public void onDisable() {
        if (module != null) {
            module.shutdown();
            module = null;
        }
    }
    /**
     * A start that failed shuts the door rather than the plugin. Disabled, the plugin left an offline server open to
     * anybody under any name; enabled with only this, it refuses every login until its staff fix the cause.
     *
     * <p>Every step is guarded on its own, so one that fails never skips the next. Whoever is online is sent away
     * too: after a failed reload nothing holds the players who were waiting to log in.
     */
    private void lock(@Nullable PluginModule wired) {
        try {
            if (wired != null) {
                wired.shutdown();
            }
        } catch (Throwable halfBuilt) {
            getLogger().log(Level.WARNING, "What the failed start had opened did not all close", halfBuilt);
        }
        try {
            // What the failed start registered answers out of a graph that is not whole.
            HandlerList.unregisterAll(this);
        } catch (Throwable stuck) {
            getLogger().log(Level.WARNING, "The listeners of the failed start could not all be removed", stuck);
        }
        try {
            getServer().getPluginManager().registerEvents(new LockedDoor(), this);
        } catch (Throwable unregistered) {
            getLogger().log(Level.SEVERE, "The locked door could not be put up", unregistered);
        }
        sendEveryoneAway();
        getLogger().severe("Every login is refused until uxmAuth starts. Fix the cause above, then restart.");
    }

    /** Sends every online player away with the locked door's words, each on their own thread. */
    private void sendEveryoneAway() {
        Component reason = LockedDoor.reason();
        try {
            for (Player online : getServer().getOnlinePlayers()) {
                // The module's scheduler may be what failed, so the player's own scheduler is asked directly.
                ScheduledTask unused = online.getScheduler().run(this, task -> online.kick(reason), null);
            }
        } catch (Throwable unreached) {
            getLogger().log(Level.SEVERE, "The players online could not all be sent away", unreached);
        }
    }

    /** Start the anonymous counts, unless this plugin has no id yet. */
    private void startMetrics() {
        if (METRICS_ID <= 0) {
            return;
        }
        new Metrics(this, METRICS_ID);
    }
}
