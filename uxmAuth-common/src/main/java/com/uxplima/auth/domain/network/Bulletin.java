package com.uxplima.auth.domain.network;

import java.util.Objects;
import java.util.UUID;

/** Something every member of a network hears at once, the proxies and the servers alike. */
public sealed interface Bulletin {

    /**
     * Every session of this account ended, because its password changed. {@code keepOnline} keeps the login of
     * the player who changed it themselves: only the sessions they are not in end.
     */
    record SessionsEnded(UUID account, boolean keepOnline) implements Bulletin {
        public SessionsEnded {
            Objects.requireNonNull(account, "account");
        }
    }
}
