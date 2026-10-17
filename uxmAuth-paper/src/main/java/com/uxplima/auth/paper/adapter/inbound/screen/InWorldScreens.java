package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Objects;
import java.util.UUID;
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
    private final ScreenShower shower;

    public InWorldScreens(
            Supplier<ScreenSettings> settings,
            AuthScreens screens,
            Predicate<UUID> sentToTheWorld,
            ScreenShower shower) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.screens = Objects.requireNonNull(screens, "screens");
        this.sentToTheWorld = Objects.requireNonNull(sentToTheWorld, "sentToTheWorld");
        this.shower = Objects.requireNonNull(shower, "shower");
    }

    @Override
    public void offer(Player player, AuthState state) {
        if (settings.get().waitingRoom() == ScreenSettings.WaitingRoom.DIALOG
                && sentToTheWorld.test(player.getUniqueId())) {
            return;
        }
        switch (state) {
            case AuthState.Unregistered unregistered -> shower.show(player, screens.register(player, null));
            case AuthState.Registered registered -> shower.show(player, screens.login(player, null));
            case AuthState.Authenticated authenticated -> {}
        }
    }
}
