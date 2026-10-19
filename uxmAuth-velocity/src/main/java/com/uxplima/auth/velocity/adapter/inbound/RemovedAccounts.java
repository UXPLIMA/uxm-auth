package com.uxplima.auth.velocity.adapter.inbound;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.proxy.Player;

/**
 * What the proxy does when staff remove an account, or change how it logs in: its player leaves the network,
 * whichever server they stand on, with the reason, and the proxy forgets their login.
 */
public final class RemovedAccounts implements Consumer<Bulletin> {

    private final Function<UUID, Optional<Player>> online;
    private final ProxySessions sessions;
    private final Messages messages;

    /** @param online the player of this account on the proxy, when there is one */
    public RemovedAccounts(Function<UUID, Optional<Player>> online, ProxySessions sessions, Messages messages) {
        this.online = Objects.requireNonNull(online, "online");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void accept(Bulletin bulletin) {
        switch (Objects.requireNonNull(bulletin, "bulletin")) {
            case Bulletin.Unregistered removed ->
                sendAway(
                        removed.account(),
                        removed.by() == Bulletin.By.OWNER ? Keys.ACCOUNT_DELETED_KICK : Keys.STAFF_REMOVED_KICK);
            case Bulletin.Rejoin rejoin ->
                sendAway(
                        rejoin.account(),
                        rejoin.by() == Bulletin.By.OWNER ? Keys.ACCOUNT_CRACKED_KICK : Keys.STAFF_REJOIN_KICK);
            case Bulletin.SessionsEnded ended -> {}
            case Bulletin.LetIn letIn -> {}
            case Bulletin.Alert alert -> {}
        }
    }

    private void sendAway(UUID account, Keys reason) {
        sessions.loggedOut(account);
        online.apply(account).ifPresent(player -> player.disconnect(messages.render(player, reason)));
    }
}
