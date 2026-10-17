package com.uxplima.auth.paper.adapter.inbound.command;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.premium.ClaimName;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * {@code /claim}: "this name is mine". The player is sent away to join again from the official launcher, and
 * that next connection from the same address is asked to prove the name.
 */
@FromConfig(
        value = ClaimCommand.KEY,
        fallbackName = "claim",
        description = "Say this name is yours, and prove it on your next connection")
public final class ClaimCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "claim";

    private final ClaimName claim;
    private final BooleanSupplier available;
    private final Messages messages;

    /** @param available whether this server runs the handshake a claim asks for */
    public ClaimCommand(ClaimName claim, BooleanSupplier available, Messages messages) {
        this.claim = Objects.requireNonNull(claim, "claim");
        this.available = Objects.requireNonNull(available, "available");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Subcommand("")
    @Permission("uxmauth.claim")
    @PlayerOnly
    public void claim(Sender sender) {
        attempt((Player) sender.bukkit());
    }

    void attempt(Player player) {
        Claims.send(player, claim, available, messages);
    }

    /** What {@code /claim} and {@code /premium} both do once the player may claim. */
    static final class Claims {

        private Claims() {}

        static void send(Player player, ClaimName claim, BooleanSupplier available, Messages messages) {
            if (!available.getAsBoolean()) {
                messages.send(player, Keys.CLAIM_UNAVAILABLE);
                return;
            }
            InetSocketAddress address = player.getAddress();
            if (address == null) {
                return;
            }
            switch (claim.claim(player.getUniqueId(), address.getAddress().getHostAddress())) {
                case ASKED -> player.kick(messages.render(player, Keys.KICK_CLAIMED));
                case ALREADY_PREMIUM -> messages.send(player, Keys.CLAIM_ALREADY_PREMIUM);
                case NOT_THIS_RULE -> messages.send(player, Keys.CLAIM_NOT_THIS_RULE);
                case GONE -> {
                    // The player is leaving; there is nobody to answer.
                }
            }
        }
    }
}
