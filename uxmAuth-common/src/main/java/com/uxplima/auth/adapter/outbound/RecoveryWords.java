package com.uxplima.auth.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.auth.application.mail.RecoverPassword;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * What each answer to a forgotten password says, on a server and on the proxy alike. A refused password says why
 * through {@link PasswordWords}, and a ban sends the player away, so neither has a line here.
 */
public final class RecoveryWords {

    /** A key and the values it is filled with. */
    public record Line(Keys key, List<TagResolver> values) {
        public Line {
            Objects.requireNonNull(key, "key");
            values = List.copyOf(values);
        }

        /** Sends the line on the channel the operator gave its key. */
        public void send(Messages messages, Audience reader) {
            messages.send(reader, key, values.toArray(TagResolver[]::new));
        }

        public Component render(Messages messages, Audience reader) {
            return messages.render(reader, key, values.toArray(TagResolver[]::new));
        }
    }

    private RecoveryWords() {}

    /** The line of this answer, or nothing for a refused password and a ban. */
    public static Optional<Line> line(RecoverPassword.Outcome outcome) {
        return Optional.ofNullable(
                switch (Objects.requireNonNull(outcome, "outcome")) {
                    case RecoverPassword.Outcome.Sent sent ->
                        new Line(
                                Keys.RECOVER_SENT,
                                List.of(
                                        Text.placeholder("address", sent.masked()),
                                        Text.placeholder(
                                                "minutes",
                                                String.valueOf(sent.lasts().toMinutes()))));
                    case RecoverPassword.Outcome.Cooldown cooldown ->
                        new Line(
                                Keys.RECOVER_COOLDOWN,
                                List.of(Text.placeholder("seconds", String.valueOf(seconds(cooldown)))));
                    case RecoverPassword.Outcome.Wrong wrong ->
                        new Line(
                                Keys.RECOVER_WRONG,
                                List.of(Text.placeholder("left", String.valueOf(wrong.attemptsLeft()))));
                    case RecoverPassword.Outcome.Rejected rejected -> null;
                    case RecoverPassword.Outcome.Banned banned -> null;
                    default -> new Line(plain(outcome), List.of());
                });
    }

    /** The seconds left, a part of one counted whole, never fewer than one. */
    private static long seconds(RecoverPassword.Outcome.Cooldown cooldown) {
        return Math.max(1, (cooldown.left().toMillis() + 999) / 1000);
    }

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
            default -> throw new IllegalStateException("an answer with values of its own: " + outcome);
        };
    }
}
