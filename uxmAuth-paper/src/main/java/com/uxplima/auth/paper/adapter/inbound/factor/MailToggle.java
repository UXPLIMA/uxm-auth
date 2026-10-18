package com.uxplima.auth.paper.adapter.inbound.factor;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.TimeWords;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.mail.MailFactor;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.command.Addresses;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * {@code /2fa email on|off} in the world: the login code by mail, for a player who will not install an app. The
 * table is read and a code checked on the hashing lane; the player is answered on their own thread.
 */
public final class MailToggle {

    private final MailFactor factor;
    private final Sessions sessions;
    private final HashingLane lane;
    private final Replies replies;
    private final MomentRunner moments;
    private final Messages messages;
    private final TimeWords times;

    public MailToggle(
            MailFactor factor,
            Sessions sessions,
            HashingLane lane,
            Replies replies,
            MomentRunner moments,
            Messages messages) {
        this.factor = Objects.requireNonNull(factor, "factor");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.moments = Objects.requireNonNull(moments, "moments");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.times = new TimeWords(messages);
    }

    /** {@code /2fa email on}. */
    public CompletableFuture<Void> on(Player player) {
        Objects.requireNonNull(player, "player");
        Optional<UUID> account = account(player);
        if (account.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        UUID id = account.get();
        return replies.on(player, lane.submit(() -> factor.on(id)), outcome -> answer(player, outcome));
    }

    /** {@code /2fa email off <code>}: a code that passes turns it off; a wrong one counts against the address. */
    public CompletableFuture<Void> off(Player player, String code) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(code, "code");
        Optional<UUID> account = account(player);
        if (account.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        UUID id = account.get();
        String address = Addresses.of(player);
        String host =
                sessions.held(player.getUniqueId()).map(Sessions.Held::host).orElse("");
        return replies.on(
                player, lane.submit(() -> factor.off(id, code, address, host)), outcome -> answer(player, outcome));
    }

    private void answer(Player player, MailFactor.Outcome outcome) {
        switch (outcome) {
            case MailFactor.Outcome.On on ->
                messages.send(player, Keys.FACTOR_MAIL_ON, Text.placeholder("address", on.masked()));
            case MailFactor.Outcome.Off off -> messages.send(player, Keys.FACTOR_MAIL_OFF);
            case MailFactor.Outcome.AlreadyOn already -> messages.send(player, Keys.FACTOR_MAIL_ALREADY_ON);
            case MailFactor.Outcome.NotOn notOn -> messages.send(player, Keys.FACTOR_MAIL_NOT_ON);
            case MailFactor.Outcome.NoAddress none -> messages.send(player, Keys.FACTOR_MAIL_NO_ADDRESS);
            case MailFactor.Outcome.Wrong wrong -> messages.send(player, Keys.FACTOR_WRONG);
            case MailFactor.Outcome.Banned banned -> {
                moments.fire(player, AuthSettings.Moment.BANNED);
                player.kick(messages.render(player, Keys.KICK_BANNED, times.placeholder(player, banned.lasts())));
            }
        }
    }

    private Optional<UUID> account(Player player) {
        if (sessions.state(player.getUniqueId()).orElse(null) instanceof AuthState.Authenticated in) {
            return Optional.of(in.account().id());
        }
        messages.send(player, Keys.FACTOR_LOG_IN_FIRST);
        return Optional.empty();
    }
}
