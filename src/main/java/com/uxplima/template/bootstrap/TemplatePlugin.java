package com.uxplima.template.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

import org.bstats.bukkit.Metrics;
import org.jspecify.annotations.Nullable;

/**
 * The plugin shell. It owns the Bukkit lifecycle and nothing else: the graph is built in
 * {@link PluginModule}, so this class stays small enough to read at a glance.
 *
 * <p>It is not final, because the mock server loads a plugin by subclassing it, and the test that enables the
 * whole plugin loads this one. Nothing of the shell is written for a subclass.
 */
public class TemplatePlugin extends JavaPlugin {

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
        PluginModule wired = new PluginModule(this);
        wired.wire();
        module = wired;
        startMetrics();
    }

    @Override
    public void onDisable() {
        if (module != null) {
            module.shutdown();
            module = null;
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
