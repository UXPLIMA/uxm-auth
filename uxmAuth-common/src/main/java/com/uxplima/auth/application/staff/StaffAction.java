package com.uxplima.auth.application.staff;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * What staff can do to an account, each under the key its branch has in commands.conf. A destructive one runs
 * from the console only, unless the operator opens it in game: a hijacked staff account cannot unregister the rest,
 * and cannot hand an account to somebody else either, which a forced login does for whoever waits under the name
 * and marking premium does for whoever holds the paid name.
 */
public enum StaffAction {
    FORCE_LOGIN("forcelogin", true),
    FORCE_REGISTER("forceregister", false),
    UNREGISTER("unregister", true),
    SET_PASSWORD("setpassword", true),
    DISABLE_2FA("disable2fa", true),
    MARK_PREMIUM("markpremium", true),
    MARK_CRACKED("markcracked", true),
    UNBAN("unban", false),
    RESOLVE("resolve", true);

    private final String key;
    private final boolean destructive;

    StaffAction(String key, boolean destructive) {
        this.key = key;
        this.destructive = destructive;
    }

    /** The key of its branch in commands.conf, which is also how staff.in-game names it. */
    public String key() {
        return key;
    }

    /** Whether it runs only from the console unless the operator opens it in game. */
    public boolean destructive() {
        return destructive;
    }

    /** The action under this key, whatever its case. */
    public static Optional<StaffAction> named(String key) {
        String wanted = Objects.requireNonNull(key, "key").strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(action -> action.key.equals(wanted))
                .findFirst();
    }
}
