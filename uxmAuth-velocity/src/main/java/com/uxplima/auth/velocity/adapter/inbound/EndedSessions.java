package com.uxplima.auth.velocity.adapter.inbound;

import java.util.Objects;
import java.util.function.Consumer;

import com.uxplima.auth.domain.network.Bulletin;

/**
 * What the proxy does when an account's sessions end: a player of it logged in here waits again, unless theirs
 * is the session that made the change, on the visit the bulletin keeps. The server they stand on hears the same bulletin and shows them the door.
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
                if (!ended.keep().equals(sessions.visit(ended.account()))) {
                    sessions.loggedOut(ended.account());
                }
            }
            // Staff letting a player in is answered where the player waits.
            case Bulletin.LetIn letIn -> {}
            // A removed account's player is sent away by RemovedAccounts.
            case Bulletin.Unregistered removed -> {}
        }
    }
}
