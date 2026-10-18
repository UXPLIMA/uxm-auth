package com.uxplima.auth.paper.adapter.inbound.mail;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.PasswordWords;
import com.uxplima.auth.adapter.outbound.TimeWords;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.mail.RecoverPassword;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.paper.adapter.inbound.command.Addresses;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * A forgotten password in the world. A player at the login asks a code at the linked address, then types it with a
 * new password; a Bedrock player gets a form for both once the code is sent. The use case reads the table and
 * hashes on the hashing lane; the player is answered on their own thread.
 */
public final class Recovery {

    /** The Bedrock form for the code and the new password, which come back to {@code answer}. */
    @FunctionalInterface
    public interface Form {
        /** No form: chat is all there is. */
        Form NONE = (player, answer) -> {};

        void show(Player player, BiConsumer<String, String> answer);
    }

    private final RecoverPassword recover;
    private final Sessions sessions;
    private final Form form;
    private final Predicate<UUID> bedrock;
    private final Replies replies;
    private final MomentRunner moments;
    private final Messages messages;
    private final TimeWords times;

    public Recovery(
            RecoverPassword recover,
            Sessions sessions,
            Form form,
            Predicate<UUID> bedrock,
            Replies replies,
            MomentRunner moments,
            Messages messages) {
        this.recover = Objects.requireNonNull(recover, "recover");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.form = Objects.requireNonNull(form, "form");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.moments = Objects.requireNonNull(moments, "moments");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.times = new TimeWords(messages);
    }

    /** {@code /recover}: a code to the linked address. */
    public CompletableFuture<Void> request(Player player) {
        Objects.requireNonNull(player, "player");
        return replies.on(
                player,
                recover.request(player.getUniqueId(), messages.localeOf(player)),
                outcome -> answer(player, outcome));
    }

    /** {@code /recover <code> <new password>}: the code that went to the address sets the new password. */
    public CompletableFuture<Void> recover(Player player, String code, String fresh) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(fresh, "fresh");
        String address = Addresses.of(player);
        String host =
                sessions.held(player.getUniqueId()).map(Sessions.Held::host).orElse("");
        return replies.on(
                player,
                recover.recover(player.getUniqueId(), code, Password.of(fresh), address, host),
                outcome -> answer(player, outcome));
    }

    private void answer(Player player, RecoverPassword.Outcome outcome) {
        switch (outcome) {
            case RecoverPassword.Outcome.Sent sent -> sent(player, sent);
            case RecoverPassword.Outcome.Cooldown cooldown ->
                messages.send(
                        player,
                        Keys.RECOVER_COOLDOWN,
                        Text.placeholder(
                                "seconds",
                                String.valueOf(Math.max(1, cooldown.left().toSeconds()))));
            case RecoverPassword.Outcome.Wrong wrong ->
                messages.send(
                        player, Keys.RECOVER_WRONG, Text.placeholder("left", String.valueOf(wrong.attemptsLeft())));
            case RecoverPassword.Outcome.Rejected rejected ->
                PasswordWords.reason(messages, player, rejected.why()).ifPresent(player::sendMessage);
            case RecoverPassword.Outcome.Banned banned -> {
                moments.fire(player, AuthSettings.Moment.BANNED);
                player.kick(messages.render(player, Keys.KICK_BANNED, times.placeholder(player, banned.lasts())));
            }
            default -> messages.send(player, plain(outcome));
        }
    }

    /** The answers that carry nothing but their words. */
    private static Keys plain(RecoverPassword.Outcome outcome) {
        return switch (outcome) {
            case RecoverPassword.Outcome.NoAddress none -> Keys.RECOVER_NO_ADDRESS;
            case RecoverPassword.Outcome.MailOff off -> Keys.EMAIL_MAIL_OFF;
            case RecoverPassword.Outcome.MailFailed failed -> Keys.EMAIL_MAIL_FAILED;
            case RecoverPassword.Outcome.NotWaiting notWaiting -> Keys.RECOVER_NOT_WAITING;
            case RecoverPassword.Outcome.Recovered recovered -> Keys.RECOVER_DONE;
            case RecoverPassword.Outcome.Expired expired -> Keys.RECOVER_EXPIRED;
            case RecoverPassword.Outcome.NothingPending nothing -> Keys.RECOVER_NOTHING_PENDING;
            case RecoverPassword.Outcome.TooManyAttempts tooMany -> Keys.RECOVER_TOO_MANY_ATTEMPTS;
            default -> throw new IllegalStateException("an answer with words of its own: " + outcome);
        };
    }

    private void sent(Player player, RecoverPassword.Outcome.Sent sent) {
        messages.send(
                player,
                Keys.RECOVER_SENT,
                Text.placeholder("address", sent.masked()),
                Text.placeholder("minutes", String.valueOf(sent.lasts().toMinutes())));
        if (bedrock.test(player.getUniqueId())) {
            form.show(player, (code, fresh) -> {
                CompletableFuture<Void> unused = recover(player, code, fresh);
            });
        }
    }
}
