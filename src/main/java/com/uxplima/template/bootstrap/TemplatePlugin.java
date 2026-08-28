package com.uxplima.template.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

import org.jspecify.annotations.Nullable;

/**
 * The plugin shell. It owns the Bukkit lifecycle and nothing else: the graph is built in
 * {@link PluginModule}, so this class stays small enough to read at a glance.
 */
public final class TemplatePlugin extends JavaPlugin {

    private @Nullable PluginModule module;

    @Override
    public void onEnable() {
        PluginModule wired = new PluginModule(this);
        wired.wire();
        module = wired;
    }

    @Override
    public void onDisable() {
        if (module != null) {
            module.shutdown();
            module = null;
        }
    }
}
