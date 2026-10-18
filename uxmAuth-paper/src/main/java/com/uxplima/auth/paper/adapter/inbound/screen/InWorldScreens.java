package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.hold.ScreenOffer;

/**
 * Shows a held player the screen for their case, while the waiting room is in the world.
 *
 * <p>In dialog mode the dialog before the world asks first. A player it sent to the world, with a client too old
 * to draw it or after leaving it, is shown nothing there and the commands are their way in. Anyone else held in
 * the world, after a logout or because they were online when the plugin started, is shown the screen.
 */
public final class InWorldScreens implements ScreenOffer {

    private final Supplier<ScreenSettings> settings;
    private final AuthScreens screens;
    private final Predicate<UUID> sentToTheWorld;
    private final BiConsumer<Player, Runnable> later;
    private final Predicate<Player> stillWaiting;
    private final ScreenShower shower;
    private final CaptchaScreen captcha;
    private final FactorOffer factor;

    /**
     * @param later runs the showing a moment after the join, when the client can draw a screen: a Bedrock client
     *     that is sent a form while it is still spawning never shows it
     * @param stillWaiting whether the player still waits when that moment comes
     */
    public InWorldScreens(
            Supplier<ScreenSettings> settings,
            AuthScreens screens,
            Predicate<UUID> sentToTheWorld,
            BiConsumer<Player, Runnable> later,
            Predicate<Player> stillWaiting,
            ScreenShower shower,
            CaptchaScreen captcha) {
        this(settings, screens, sentToTheWorld, later, stillWaiting, shower, captcha, FactorOffer.NONE);
    }

    /** @param factor the code screen of the second step, for a player who gave the password already */
    public InWorldScreens(
            Supplier<ScreenSettings> settings,
            AuthScreens screens,
            Predicate<UUID> sentToTheWorld,
            BiConsumer<Player, Runnable> later,
            Predicate<Player> stillWaiting,
            ScreenShower shower,
            CaptchaScreen captcha,
            FactorOffer factor) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.screens = Objects.requireNonNull(screens, "screens");
        this.sentToTheWorld = Objects.requireNonNull(sentToTheWorld, "sentToTheWorld");
        this.later = Objects.requireNonNull(later, "later");
        this.stillWaiting = Objects.requireNonNull(stillWaiting, "stillWaiting");
        this.shower = Objects.requireNonNull(shower, "shower");
        this.captcha = Objects.requireNonNull(captcha, "captcha");
        this.factor = Objects.requireNonNull(factor, "factor");
    }

    @Override
    public void offer(Player player, AuthState state) {
        // A client that cannot show the register screen still reads the rules it accepts by registering.
        if (state instanceof AuthState.Unregistered) {
            screens.rulesInChat(player).forEach(player::sendMessage);
        }
        if (settings.get().waitingRoom() == ScreenSettings.WaitingRoom.DIALOG
                && sentToTheWorld.test(player.getUniqueId())) {
            return;
        }
        later.accept(player, () -> {
            if (!player.isOnline() || !stillWaiting.test(player)) {
                return;
            }
            if (captcha.stands(player.getUniqueId(), state)) {
                captcha.offer(player, player.getUniqueId(), null);
                return;
            }
            switch (state) {
                case AuthState.Unregistered unregistered -> shower.show(player, screens.register(player, null));
                case AuthState.Registered registered -> shower.show(player, screens.login(player, null));
                case AuthState.Replacing replacing -> shower.show(player, screens.replace(player, replacing.why()));
                case AuthState.SecondFactor waiting -> factor.offer(player, null);
                // The setup is in chat, on the map and on a Bedrock form: no screen of the login stands for it.
                case AuthState.SettingUp held -> {}
                case AuthState.Authenticated authenticated -> {}
            }
        });
    }
}
