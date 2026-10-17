package com.uxplima.auth.adapter.outbound;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Where a player waits, and every screen they may be shown, as {@code screens.conf} writes them.
 *
 * <p>A text that starts with {@code @} names a line of the catalogue, read in the player's language; any
 * other text is the operator's own MiniMessage, drawn as written.
 */
public record ScreenSettings(
        WaitingRoom waitingRoom,
        Cancel cancel,
        boolean canClose,
        boolean pinPad,
        Rules rules,
        Screen register,
        Screen login,
        Screen pin,
        String loginWorld) {

    /** The world a waiting player is moved to while the waiting room is the login world, unless the file names one. */
    public static final String LOGIN_WORLD = "uxmauth_login";

    public ScreenSettings {
        Objects.requireNonNull(loginWorld, "loginWorld");
        if (loginWorld.isBlank()) {
            throw new IllegalArgumentException("login-world-name names no world");
        }
        Objects.requireNonNull(waitingRoom, "waitingRoom");
        Objects.requireNonNull(cancel, "cancel");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(register, "register");
        Objects.requireNonNull(login, "login");
        Objects.requireNonNull(pin, "pin");
    }

    /** The screens with the login world under its usual name. */
    public ScreenSettings(
            WaitingRoom waitingRoom,
            Cancel cancel,
            boolean canClose,
            boolean pinPad,
            Rules rules,
            Screen register,
            Screen login,
            Screen pin) {
        this(waitingRoom, cancel, canClose, pinPad, rules, register, login, pin, LOGIN_WORLD);
    }

    /** Where an unauthenticated player waits. */
    public enum WaitingRoom {
        /** A dialog before the world, during the configuration phase. */
        DIALOG,
        /** Frozen in the world, with the commands and the in-world dialogs. */
        FROZEN,
        /** An empty world of this plugin's own, and back to their place after the login. */
        LOGIN_WORLD
    }

    /** What closing the dialog does. */
    public enum Cancel {
        KICK,
        /** Into the frozen world, where the commands still work. */
        WORLD
    }

    /** The rules a player accepts before registering. */
    public record Rules(boolean enabled, List<String> lines) {
        public Rules {
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        }
    }

    /** One screen: its title, its body lines in order, and the label of each field and button. */
    public record Screen(String title, List<String> body, Map<String, String> labels) {
        public Screen {
            Objects.requireNonNull(title, "title");
            body = List.copyOf(Objects.requireNonNull(body, "body"));
            labels = Map.copyOf(Objects.requireNonNull(labels, "labels"));
        }

        /** The label of one field or button, or an empty line when the file names none. */
        public String label(String name) {
            return labels.getOrDefault(Objects.requireNonNull(name, "name"), "");
        }
    }
}
