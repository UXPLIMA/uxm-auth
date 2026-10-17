package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.premium.ClaimName;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * {@code /premium}: the owner of an account, logged in, asks to prove its name from the next connection on. When
 * that connection passes, the account turns premium and keeps everything it had.
 */
@FromConfig(
        value = PremiumCommand.KEY,
        fallbackName = "premium",
        description = "Prove your name on your next connection and log in without a password from then on")
public final class PremiumCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "premium";

    private final ClaimName claim;
    private final BooleanSupplier available;
    private final Sessions sessions;
    private final Messages messages;

    public PremiumCommand(ClaimName claim, BooleanSupplier available, Sessions sessions, Messages messages) {
        this.claim = Objects.requireNonNull(claim, "claim");
        this.available = Objects.requireNonNull(available, "available");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Subcommand("")
    @Permission("uxmauth.premium")
    @PlayerOnly
    public void premium(Sender sender) {
        attempt((Player) sender.bukkit());
    }

    void attempt(Player player) {
        if (!(sessions.state(player.getUniqueId()).orElse(null) instanceof AuthState.Authenticated)) {
            messages.send(player, Keys.PREMIUM_LOG_IN_FIRST);
            return;
        }
        ClaimCommand.Claims.send(player, claim, available, messages);
    }
}
