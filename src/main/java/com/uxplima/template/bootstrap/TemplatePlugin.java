package com.uxplima.template.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * The plugin shell. It owns the Bukkit lifecycle and nothing else: the graph is built in
 * {@link PluginModule}, so this class stays small enough to read at a glance.
 */
public final class TemplatePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        new PluginModule(this).wire();
    }
}
