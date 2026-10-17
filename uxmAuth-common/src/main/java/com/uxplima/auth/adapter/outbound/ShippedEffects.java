package com.uxplima.auth.adapter.outbound;

import java.util.List;

/** The effect lists the shipped file carries, for a file that does not name a moment at all. */
final class ShippedEffects {

    private static final String CHIME = "[sound] block.note_block.chime 0.6 1.2";

    private ShippedEffects() {}

    static List<String> of(AuthSettings.Moment moment) {
        return switch (moment) {
            case JOIN_UNREGISTERED -> List.of("[subtitle] @join.register-title | @join.register-subtitle");
            case JOIN_REGISTERED -> List.of("[subtitle] @join.login-title | @join.login-subtitle");
            case REMINDER_REGISTER -> List.of("[actionbar] @reminder.register");
            case REMINDER_LOGIN -> List.of("[actionbar] @reminder.login");
            case WRONG_PASSWORD -> List.of("[sound] block.note_block.bass 0.8 0.6");
            case REGISTERED -> List.of("[title] @welcome.registered", CHIME);
            case LOGGED_IN -> List.of("[title] @welcome.logged-in", CHIME);
            case TIMEOUT, TOO_MANY_ATTEMPTS -> List.of();
            case LOGGED_OUT -> List.of("[sound] block.note_block.hat 0.8 1.0");
            case PASSWORD_CHANGED -> List.of(CHIME);
        };
    }
}
