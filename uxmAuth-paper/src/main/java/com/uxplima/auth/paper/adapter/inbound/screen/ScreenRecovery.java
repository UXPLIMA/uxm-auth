package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.PasswordWords;
import com.uxplima.auth.adapter.outbound.RecoveryWords;
import com.uxplima.auth.adapter.outbound.TimeWords;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.mail.RecoverPassword;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * "Forgot password" on the login screen, for a player in the dialog before the world who cannot type /recover. A
 * code that went out, or one still on its way, opens the recovery screen; a wrong code or a refused password stays
 * there with the reason; anything else goes back to the login screen with what happened on top.
 */
public final class ScreenRecovery implements RecoveryOffer {

    private final RecoverPassword recover;
    private final Sessions sessions;
    private final AuthScreens screens;
    private final ScreenShower shower;
    private final Messages messages;
    private final TimeWords times;

    public ScreenRecovery(
            RecoverPassword recover, Sessions sessions, AuthScreens screens, ScreenShower shower, Messages messages) {
        this.recover = Objects.requireNonNull(recover, "recover");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.screens = Objects.requireNonNull(screens, "screens");
        this.shower = Objects.requireNonNull(shower, "shower");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.times = new TimeWords(messages);
    }

    @Override
    public CompletableFuture<Void> forgot(Guest guest) {
        Audience reader = Objects.requireNonNull(guest, "guest").audience();
        return recover.request(guest.id(), messages.localeOf(reader)).thenAccept(outcome -> shown(guest, outcome));
    }

    @Override
    public CompletableFuture<Void> recover(Guest guest, String code, String fresh) {
        Objects.requireNonNull(guest, "guest");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(fresh, "fresh");
        if (code.isBlank() || fresh.isEmpty()) {
            Audience reader = guest.audience();
            shower.show(reader, screens.recover(reader, messages.render(reader, Keys.RECOVER_USAGE)));
            return CompletableFuture.completedFuture(null);
        }
        String host = sessions.held(guest.id()).map(Sessions.Held::host).orElse("");
        return recover.recover(guest.id(), code.strip(), Password.of(fresh), guest.address(), host)
                .thenAccept(outcome -> shown(guest, outcome));
    }

    private void shown(Guest guest, RecoverPassword.Outcome outcome) {
        Audience reader = guest.audience();
        switch (outcome) {
            case RecoverPassword.Outcome.Banned banned ->
                guest.kick(messages.render(reader, Keys.KICK_BANNED, times.placeholder(reader, banned.lasts())));
            case RecoverPassword.Outcome.Rejected rejected ->
                shower.show(
                        reader,
                        screens.recover(
                                reader,
                                PasswordWords.reason(messages, reader, rejected.why())
                                        .orElse(null)));
            case RecoverPassword.Outcome.Sent sent -> shower.show(reader, screens.recover(reader, line(reader, sent)));
            case RecoverPassword.Outcome.Cooldown cooldown ->
                shower.show(reader, screens.recover(reader, line(reader, cooldown)));
            case RecoverPassword.Outcome.Wrong wrong ->
                shower.show(reader, screens.recover(reader, line(reader, wrong)));
            default -> shower.show(reader, screens.login(reader, line(reader, outcome)));
        }
    }

    private Component line(Audience reader, RecoverPassword.Outcome outcome) {
        return RecoveryWords.line(outcome)
                .map(said -> said.render(messages, reader))
                .orElseGet(() -> messages.render(reader, Keys.RECOVER_USAGE));
    }
}
