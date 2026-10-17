package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.hold.ScreenOffer;

/**
 * Shows a held player the screen for their case, while the waiting room is in the world.
 *
 * <p>In dialog mode a player who reaches the world unchecked either has a client that cannot show a dialog or
 * closed it on purpose, so the world shows them nothing and the commands are their way in.
 */
public final class InWorldScreens implements ScreenOffer {

    private final Supplier<ScreenSettings> settings;
    private final AuthScreens screens;
    private final ScreenShower shower;

    public InWorldScreens(Supplier<ScreenSettings> settings, AuthScreens screens, ScreenShower shower) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.screens = Objects.requireNonNull(screens, "screens");
        this.shower = Objects.requireNonNull(shower, "shower");
    }

    @Override
    public void offer(Player player, AuthState state) {
        if (settings.get().waitingRoom() == ScreenSettings.WaitingRoom.DIALOG) {
            return;
        }
        switch (state) {
            case AuthState.Unregistered unregistered -> shower.show(player, screens.register(player, null));
            case AuthState.Registered registered -> shower.show(player, screens.login(player, null));
            case AuthState.Authenticated authenticated -> {}
        }
    }
}
