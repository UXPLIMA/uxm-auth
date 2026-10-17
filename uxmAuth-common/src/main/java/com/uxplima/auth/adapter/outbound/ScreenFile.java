package com.uxplima.auth.adapter.outbound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmlib.config.HoconConfig;

/** {@code screens.conf}, read into one immutable {@link ScreenSettings} and swapped whole on reload. */
public final class ScreenFile {

    /** The fields and buttons each screen labels, in the order a screen draws them. */
    private static final Map<String, List<String>> LABELS = Map.of(
            "register", List.of("password", "confirm", "accept", "submit", "cancel"),
            "login", List.of("password", "submit", "cancel"),
            "pin", List.of("erase", "enter", "cancel", "entered", "repeat", "mask"));

    private final Path file;
    private volatile ScreenSettings current;

    public ScreenFile(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.current = read();
    }

    /** Adds to an operator's file every key a release added, keeping what they wrote. */
    public static boolean bringUpToDate(Path file, ClassLoader resources) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(resources, "resources");
        if (!Files.isRegularFile(file)) {
            return false;
        }
        return HoconConfig.load(file).mergeDefaults("screens.conf", resources);
    }

    public ScreenSettings current() {
        return current;
    }

    /** Reads the file again. A file that cannot work leaves the screens as they were and says why. */
    public void reload() {
        this.current = read();
    }

    private ScreenSettings read() {
        HoconConfig config = HoconConfig.load(file);
        return new ScreenSettings(
                choice(config, "waiting-room", ScreenSettings.WaitingRoom.class, ScreenSettings.WaitingRoom.DIALOG),
                choice(config, "cancel", ScreenSettings.Cancel.class, ScreenSettings.Cancel.KICK),
                config.getBoolean("can-close", false),
                config.getBoolean("pin-pad", false),
                new ScreenSettings.Rules(
                        config.getBoolean("rules.enabled", false),
                        list(config, "rules", "lines", List.of("@rules.line-1", "@rules.line-2"))),
                screen(config, "register"),
                screen(config, "login"),
                screen(config, "pin"),
                config.getString("login-world-name", ScreenSettings.LOGIN_WORLD));
    }

    private static ScreenSettings.Screen screen(HoconConfig config, String name) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (String label : LABELS.getOrDefault(name, List.of())) {
            labels.put(label, config.getString(name + "." + label, "@screen." + name + "." + label));
        }
        return new ScreenSettings.Screen(
                config.getString(name + ".title", "@screen." + name + ".title"),
                list(config, name, "body", List.of("@screen." + name + ".body")),
                labels);
    }

    /** A list as written, or the shipped one when the file says nothing; {@code []} is the operator's nothing. */
    private static List<String> list(HoconConfig config, String block, String name, List<String> shipped) {
        if (config.nodeAt(block, name).virtual()) {
            return shipped;
        }
        return List.copyOf(config.getList(block + "." + name, String.class));
    }

    private static <E extends Enum<E>> E choice(HoconConfig config, String path, Class<E> type, E fallback) {
        String written =
                config.getString(path, fallback.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        try {
            return Enum.valueOf(type, written.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(path + " is " + written + ", which this plugin does not have", unknown);
        }
    }
}
