package com.uxplima.auth.velocity.adapter.inbound;

import java.util.Objects;
import java.util.function.Consumer;

import com.uxplima.auth.domain.network.Bulletin;

/**
 * What the proxy does when an account's sessions end: a player of it logged in here waits again, unless they
 * made the change themselves. The server they stand on hears the same bulletin and shows them the door.
 */
public final class EndedSessions implements Consumer<Bulletin> {

    private final ProxySessions sessions;

    public EndedSessions(ProxySessions sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @Override
    public void accept(Bulletin bulletin) {
        switch (Objects.requireNonNull(bulletin, "bulletin")) {
            case Bulletin.SessionsEnded ended -> {
                if (!ended.keepOnline()) {
                    sessions.loggedOut(ended.account());
                }
            }
        }
    }
}
