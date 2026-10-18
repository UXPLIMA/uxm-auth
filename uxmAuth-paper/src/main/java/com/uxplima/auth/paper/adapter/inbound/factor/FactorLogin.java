package com.uxplima.auth.paper.adapter.inbound.factor;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.screen.FactorOffer;
import com.uxplima.auth.paper.adapter.inbound.screen.Guest;
import com.uxplima.uxmlib.text.message.Messages;

/** The code of the second step typed as {@code /2fa <code>}, for a player who gave the password already. */
public final class FactorLogin {

    private final Sessions sessions;
    private final FactorOffer factor;
    private final Function<Player, Guest> guests;
    private final Messages messages;

    public FactorLogin(Sessions sessions, FactorOffer factor, Function<Player, Guest> guests, Messages messages) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.factor = Objects.requireNonNull(factor, "factor");
        this.guests = Objects.requireNonNull(guests, "guests");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Hands the code to the second step, or says that none is asked. */
    public CompletableFuture<Void> answer(Player player, String code) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(code, "code");
        if (!(sessions.state(player.getUniqueId()).orElse(null) instanceof AuthState.SecondFactor)) {
            messages.send(player, Keys.FACTOR_NOTHING_ASKED);
            return CompletableFuture.completedFuture(null);
        }
        return factor.answer(guests.apply(player), code);
    }
}
