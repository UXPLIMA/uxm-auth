package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.protection.Captchas;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * Shows the captcha and takes its answer: on a map in the hand where the operator chose it and the player can
 * hold one, on a screen otherwise, which a Bedrock player reads as a form.
 */
public final class CaptchaScreen implements CaptchaOffer {

    /** Puts the code drawn on a map in a player's hand, when the settings ask it and the player can hold it. */
    @FunctionalInterface
    public interface Hand {
        /** No map: every code goes on a screen. */
        Hand NONE = (player, code) -> false;

        /** Says whether the map is in the hand now. */
        boolean give(Player player, String code);
    }

    private final Captchas captchas;
    private final AuthScreens screens;
    private final ScreenShower shower;
    private final Messages messages;
    private final Hand hand;

    public CaptchaScreen(Captchas captchas, AuthScreens screens, ScreenShower shower, Messages messages, Hand hand) {
        this.captchas = Objects.requireNonNull(captchas, "captchas");
        this.screens = Objects.requireNonNull(screens, "screens");
        this.shower = Objects.requireNonNull(shower, "shower");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.hand = Objects.requireNonNull(hand, "hand");
    }

    /** Whether a captcha stands before what this waiting player does next: a registration, or a login. */
    public boolean stands(UUID id, AuthState state) {
        Objects.requireNonNull(id, "id");
        return switch (Objects.requireNonNull(state, "state")) {
            case AuthState.Unregistered unregistered -> captchas.blocksRegistration(id);
            case AuthState.Registered waiting ->
                captchas.blocks(id, waiting.account().name());
            case AuthState.Replacing replacing -> false;
            case AuthState.Authenticated authenticated -> false;
        };
    }

    /** Shows a fresh code, with the reason on top; the code of any earlier showing answers nothing now. */
    @Override
    public void offer(Audience reader, UUID id, @Nullable Component reason) {
        Objects.requireNonNull(reader, "reader");
        String code = captchas.show(Objects.requireNonNull(id, "id"));
        if (reader instanceof Player player && hand.give(player, code)) {
            shower.close(player);
            if (reason != null) {
                player.sendMessage(reason);
            }
            messages.send(player, Keys.CAPTCHA_MAP);
            return;
        }
        shower.show(reader, screens.captcha(reader, code, reason));
    }

    /** Whether a code waits for an answer from this connection. */
    public boolean waiting(UUID id) {
        return captchas.code(Objects.requireNonNull(id, "id")).isPresent();
    }

    /** The connection ended: its code and its solved captcha go with it. */
    public void forget(UUID id) {
        captchas.forget(Objects.requireNonNull(id, "id"));
    }

    /** Answers the latest showing, which any answer spends; says whether it was the code. */
    public boolean solve(UUID id, String typed) {
        return captchas.solve(Objects.requireNonNull(id, "id"), Objects.requireNonNull(typed, "typed"));
    }
}
