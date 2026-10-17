package com.uxplima.auth.adapter.outbound;

import java.util.List;

import com.uxplima.uxmlib.config.HoconConfig;

/** Reads the protection block of config.conf into its record. */
final class ProtectionReader {

    private ProtectionReader() {}

    static ProtectionSettings protection(HoconConfig config) {
        return new ProtectionSettings(list(config, "shared-address-hosts"));
    }

    private static List<String> list(HoconConfig config, String name) {
        String path = "protection." + name;
        if (config.nodeAt((Object[]) path.split("\\.")).virtual()) {
            return List.of();
        }
        return List.copyOf(config.getList(path, String.class));
    }
}
