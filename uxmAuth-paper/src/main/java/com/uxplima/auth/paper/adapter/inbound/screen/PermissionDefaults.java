package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

/**
 * What a client with no player yet holds: only the permissions the server grants to everyone. A permission
 * plugin answers a player, and before the world there is none to ask.
 */
public final class PermissionDefaults implements Predicate<String> {

    private final PluginManager plugins;

    public PermissionDefaults(PluginManager plugins) {
        this.plugins = Objects.requireNonNull(plugins, "plugins");
    }

    @Override
    public boolean test(String permission) {
        Permission known = plugins.getPermission(Objects.requireNonNull(permission, "permission"));
        return known != null && known.getDefault() == PermissionDefault.TRUE;
    }
}
