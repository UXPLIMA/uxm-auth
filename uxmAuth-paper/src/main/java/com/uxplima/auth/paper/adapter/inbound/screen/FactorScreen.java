package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.TimeWords;
import com.uxplima.auth.application.factor.ConfirmFactor;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The code of the second step, on its screen or after {@code /2fa}. The code is checked off the player's thread,
 * because the factor table is read; the guest is answered on theirs.
 */
public final class FactorScreen implements FactorOffer {

    private final ConfirmFactor confirm;
    private final AuthScreens screens;
    private final ScreenShower shower;
    private final Messages messages;
    private final HashingLane lane;
    private final TimeWords times;

    /** @param lane where the factor table is read and backup codes hashed, never a server or region thread */
    public FactorScreen(
            ConfirmFactor confirm, AuthScreens screens, ScreenShower shower, Messages messages, HashingLane lane) {
        this.confirm = Objects.requireNonNull(confirm, "confirm");
        this.screens = Objects.requireNonNull(screens, "screens");
        this.shower = Objects.requireNonNull(shower, "shower");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.times = new TimeWords(messages);
    }

    @Override
    public void offer(Audience reader, @Nullable Component reason) {
        shower.show(Objects.requireNonNull(reader, "reader"), screens.code(reader, reason));
    }

    @Override
    public CompletableFuture<Void> answer(Guest guest, String code) {
        Objects.requireNonNull(guest, "guest");
        Objects.requireNonNull(code, "code");
        String address = guest.address();
        return guest.after(
                lane.submit(() -> confirm.confirm(guest.id(), code, address)), result -> answered(guest, result));
    }

    private void answered(Guest guest, ConfirmFactor.Result result) {
        Audience reader = guest.audience();
        switch (result) {
            case ConfirmFactor.Result.LoggedIn in
            when in.state() instanceof AuthState.Replacing replacing ->
                shower.show(reader, screens.replace(reader, replacing.why()));
            case ConfirmFactor.Result.LoggedIn in -> {
                shower.close(reader);
                guest.letIn(AuthSettings.Moment.LOGGED_IN);
            }
            case ConfirmFactor.Result.Wrong wrong -> {
                guest.fire(AuthSettings.Moment.WRONG_PASSWORD);
                offer(
                        reader,
                        messages.render(
                                reader,
                                Keys.FACTOR_LOGIN_WRONG,
                                Text.placeholder("left", String.valueOf(wrong.attemptsLeft()))));
            }
            case ConfirmFactor.Result.TooManyAttempts tooMany -> {
                guest.fire(AuthSettings.Moment.TOO_MANY_ATTEMPTS);
                guest.kick(messages.render(reader, Keys.KICK_TOO_MANY_ATTEMPTS));
            }
            case ConfirmFactor.Result.Banned banned -> {
                guest.fire(AuthSettings.Moment.BANNED);
                guest.kick(messages.render(reader, Keys.KICK_BANNED, times.placeholder(reader, banned.lasts())));
            }
            case ConfirmFactor.Result.NotWaiting notWaiting -> shower.close(reader);
            case ConfirmFactor.Result.Gone gone -> {}
        }
    }
}
