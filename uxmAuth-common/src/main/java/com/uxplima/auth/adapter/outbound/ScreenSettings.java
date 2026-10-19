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
        String loginWorld,
        Screen replace,
        Screen captcha,
        Screen code,
        Screen recover,
        boolean chatInput) {

    /** Screens with chat answering nothing, as every file read before {@code chat-input} existed. */
    public ScreenSettings(
            WaitingRoom waitingRoom,
            Cancel cancel,
            boolean canClose,
            boolean pinPad,
            Rules rules,
            Screen register,
            Screen login,
            Screen pin,
            String loginWorld,
            Screen replace,
            Screen captcha,
            Screen code,
            Screen recover) {
        this(
                waitingRoom,
                cancel,
                canClose,
                pinPad,
                rules,
                register,
                login,
                pin,
                loginWorld,
                replace,
                captcha,
                code,
                recover,
                false);
    }

    /** The world a waiting player is moved to while the waiting room is the login world, unless the file names one. */
    public static final String LOGIN_WORLD = "uxmauth_login";

    /** The labels each screen carries, in the order the file writes them. */
    static final Map<String, List<String>> LABELS = Map.of(
            "register", List.of("password", "confirm", "accept", "remember", "submit", "cancel"),
            "login", List.of("password", "remember", "submit", "claim", "forgot", "cancel"),
            "pin", List.of("erase", "enter", "cancel", "remember", "entered", "repeat", "mask"),
            "replace", List.of("password", "confirm", "submit", "cancel"),
            "captcha", List.of("code", "submit", "cancel"),
            "code", List.of("code", "submit", "cancel"),
            "recover", List.of("code", "password", "submit", "cancel"));

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
        Objects.requireNonNull(replace, "replace");
        Objects.requireNonNull(captcha, "captcha");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(recover, "recover");
    }

    /** The screens with the shipped screen for a forgotten password. */
    public ScreenSettings(
            WaitingRoom waitingRoom,
            Cancel cancel,
            boolean canClose,
            boolean pinPad,
            Rules rules,
            Screen register,
            Screen login,
            Screen pin,
            String loginWorld,
            Screen replace,
            Screen captcha,
            Screen code) {
        this(
                waitingRoom,
                cancel,
                canClose,
                pinPad,
                rules,
                register,
                login,
                pin,
                loginWorld,
                replace,
                captcha,
                code,
                shipped("recover"));
    }

    /** The screens with the shipped screen for the code of the second step. */
    public ScreenSettings(
            WaitingRoom waitingRoom,
            Cancel cancel,
            boolean canClose,
            boolean pinPad,
            Rules rules,
            Screen register,
            Screen login,
            Screen pin,
            String loginWorld,
            Screen replace,
            Screen captcha) {
        this(
                waitingRoom,
                cancel,
                canClose,
                pinPad,
                rules,
                register,
                login,
                pin,
                loginWorld,
                replace,
                captcha,
                shipped("code"));
    }

    /** The screens with the shipped captcha screen. */
    public ScreenSettings(
            WaitingRoom waitingRoom,
            Cancel cancel,
            boolean canClose,
            boolean pinPad,
            Rules rules,
            Screen register,
            Screen login,
            Screen pin,
            String loginWorld,
            Screen replace) {
        this(
                waitingRoom,
                cancel,
                canClose,
                pinPad,
                rules,
                register,
                login,
                pin,
                loginWorld,
                replace,
                shipped("captcha"));
    }

    /** The screens with the shipped screen for replacing a weak password. */
    public ScreenSettings(
            WaitingRoom waitingRoom,
            Cancel cancel,
            boolean canClose,
            boolean pinPad,
            Rules rules,
            Screen register,
            Screen login,
            Screen pin,
            String loginWorld) {
        this(waitingRoom, cancel, canClose, pinPad, rules, register, login, pin, loginWorld, shippedReplace());
    }

    /** The screen a player held to replace a weak password reads, as the shipped file writes it. */
    public static Screen shippedReplace() {
        return shipped("replace");
    }

    /** A screen as the shipped file writes it: every line from the catalogue, under the screen's own name. */
    private static Screen shipped(String name) {
        Map<String, String> labels = new java.util.LinkedHashMap<>();
        for (String label : LABELS.getOrDefault(name, List.of())) {
            labels.put(label, "@screen." + name + "." + label);
        }
        return new Screen("@screen." + name + ".title", List.of("@screen." + name + ".body"), labels);
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
