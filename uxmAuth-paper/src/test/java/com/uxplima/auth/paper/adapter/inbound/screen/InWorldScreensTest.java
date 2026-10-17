package com.uxplima.auth.paper.adapter.inbound.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.audience.Audience;

import com.uxplima.auth.adapter.outbound.ScreenFile;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** In the frozen world a player who can see a dialog is shown one; in dialog mode the world shows none. */
final class InWorldScreensTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private final List<DialogSpec> shown = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("frozen: a player with no account is shown the register screen, one with an account the login screen")
    void frozenShowsTheScreenForTheirCase() {
        PlayerMock newcomer = server.addPlayer("Newcomer");
        PlayerMock steve = server.addPlayer("Steve");

        InWorldScreens offer = offer(ScreenSettings.WaitingRoom.FROZEN);
        offer.offer(newcomer, new AuthState.Unregistered(new PlayerName("Newcomer")));
        world.registered(steve, "hunter22");
        offer.offer(steve, world.sessions.state(steve.getUniqueId()).orElseThrow());

        assertThat(shown).hasSize(2);
        assertThat(shown.get(0).buttons()).extracting(DialogSpec.Button::action).contains(AuthScreens.REGISTER);
        assertThat(shown.get(1).buttons()).extracting(DialogSpec.Button::action).contains(AuthScreens.LOGIN);
    }

    @Test
    @DisplayName(
            "dialog mode: whoever reaches the world unchecked could not or would not use the dialog, so none is shown")
    void dialogModeShowsNothingInTheWorld() {
        PlayerMock steve = server.addPlayer("Steve");

        offer(ScreenSettings.WaitingRoom.DIALOG).offer(steve, new AuthState.Unregistered(new PlayerName("Steve")));

        assertThat(shown).isEmpty();
    }

    private InWorldScreens offer(ScreenSettings.WaitingRoom room) {
        ScreenSettings shipped = new ScreenFile(folder.resolve("screens.conf")).current();
        ScreenSettings settings = new ScreenSettings(
                room,
                shipped.cancel(),
                shipped.canClose(),
                shipped.pinPad(),
                shipped.rules(),
                shipped.register(),
                shipped.login(),
                shipped.pin());
        AuthScreens screens =
                new AuthScreens(() -> settings, () -> world.passwords, world.messages, new Styler(Theme.defaults()));
        return new InWorldScreens(() -> settings, screens, new ScreenShower() {
            @Override
            public void show(Audience who, DialogSpec spec) {
                shown.add(spec);
            }

            @Override
            public void close(Audience who) {}
        });
    }
}
