package com.uxplima.auth.velocity.adapter.inbound;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.kyori.adventure.audience.Audience;

import com.uxplima.auth.adapter.outbound.PasswordWords;
import com.uxplima.auth.adapter.outbound.RecoveryWords;
import com.uxplima.auth.adapter.outbound.TimeWords;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.mail.RecoverPassword;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * A forgotten password in the proxy's waiting world: {@code /recover} alone mails a code to the linked address,
 * and {@code /recover <code> <new password>} sets the password, after which the player logs in with it.
 */
public final class LimboRecovery implements LimboLogin.Recover {

    private final RecoverPassword recover;
    private final Sessions sessions;
    private final Messages messages;
    private final TimeWords times;

    public LimboRecovery(RecoverPassword recover, Sessions sessions, Messages messages) {
        this.recover = Objects.requireNonNull(recover, "recover");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.times = new TimeWords(messages);
    }

    @Override
    public CompletableFuture<Optional<LimboLogin.Outcome>> answer(
            Audience reader, UUID id, List<String> typed, String address) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typed, "typed");
        Objects.requireNonNull(address, "address");
        if (typed.isEmpty()) {
            return recover.request(id, messages.localeOf(reader)).thenApply(outcome -> said(reader, outcome));
        }
        if (typed.size() != 2) {
            return CompletableFuture.completedFuture(
                    Optional.of(new LimboLogin.Outcome.Stay(messages.render(reader, Keys.RECOVER_USAGE))));
        }
        String host = sessions.held(id).map(Sessions.Held::host).orElse("");
        return recover.recover(id, typed.getFirst(), Password.of(typed.get(1)), address, host)
                .thenApply(outcome -> said(reader, outcome));
    }

    private Optional<LimboLogin.Outcome> said(Audience reader, RecoverPassword.Outcome outcome) {
        return Optional.of(
                switch (outcome) {
                    case RecoverPassword.Outcome.Banned banned ->
                        new LimboLogin.Outcome.Out(
                                messages.render(reader, Keys.KICK_BANNED, times.placeholder(reader, banned.lasts())));
                    case RecoverPassword.Outcome.Rejected rejected ->
                        new LimboLogin.Outcome.Stay(PasswordWords.reason(messages, reader, rejected.why())
                                .orElseGet(() -> messages.render(reader, Keys.RECOVER_USAGE)));
                    default ->
                        new LimboLogin.Outcome.Stay(RecoveryWords.line(outcome)
                                .map(line -> line.render(messages, reader))
                                .orElseGet(() -> messages.render(reader, Keys.RECOVER_USAGE)));
                });
    }
}
