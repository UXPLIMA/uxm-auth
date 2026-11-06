package com.uxplima.auth.velocity.adapter.inbound;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerChannelUnregisterEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;

/**
 * Keeps the report channel open between a server and the proxy. The proxy opens it on every server a player
 * joins; a client that closes it again would keep its server's reports from the proxy, so the proxy would never
 * hear that the player waits again after a logout there, and would write the next server a claim that they are
 * logged in.
 *
 * <p>No client opens or closes the report channel for itself, so a client that closes it is sent away, and a
 * client's own register or unregister naming it goes no further.
 */
public final class ChannelGuard {

    /** Above every plugin that leaves the default, so the line is dropped before any other plugin passes it on. */
    private static final short EARLY = 1000;

    /** The channels a client opens and closes others with, in the names of every protocol. */
    private static final Set<String> REGISTERING =
            Set.of("minecraft:register", "minecraft:unregister", "register", "unregister");

    private final Messages words;

    public ChannelGuard(Messages words) {
        this.words = Objects.requireNonNull(words, "words");
    }

    @Subscribe
    public void onClosed(PlayerChannelUnregisterEvent event) {
        closed(event.getPlayer(), event.getChannels());
    }

    @Subscribe(priority = EARLY)
    public void onMessage(PluginMessageEvent event) {
        dropped(event);
    }

    /** The client closed these channels: one that closes the report channel is sent away. */
    void closed(Player player, List<ChannelIdentifier> channels) {
        if (channels.stream().anyMatch(channel -> channel.getId().equals(Claims.CHANNEL))) {
            player.disconnect(words.render(player, Keys.KICK_NETWORK_CHANNEL));
        }
    }

    /**
     * A client's register or unregister that names the report channel goes no further. The proxy may read such
     * lines before this event on the releases known, and the client is then sent away by {@link #closed}; this
     * holds should a release pass them here.
     */
    void dropped(PluginMessageEvent event) {
        if (!(event.getSource() instanceof Player)
                || !REGISTERING.contains(event.getIdentifier().getId().toLowerCase(Locale.ROOT))) {
            return;
        }
        for (String channel : new String(event.getData(), StandardCharsets.UTF_8).split("\0", -1)) {
            if (channel.strip().equals(Claims.CHANNEL)) {
                event.setResult(PluginMessageEvent.ForwardResult.handled());
                return;
            }
        }
    }
}
