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
 * What the proxy does when staff remove an account: its player leaves the network, whichever server they stand
 * on, and the proxy forgets what it held for them.
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
        if (Objects.requireNonNull(bulletin, "bulletin") instanceof Bulletin.Unregistered removed) {
            sessions.loggedOut(removed.account());
            online.apply(removed.account())
                    .ifPresent(player -> player.disconnect(messages.render(player, Keys.STAFF_REMOVED_KICK)));
        }
    }
}
