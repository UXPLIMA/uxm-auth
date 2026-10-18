package com.uxplima.auth.paper.adapter.inbound.factor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** The setup on a Bedrock form: the key in a field a phone copies from, and the first code. */
final class BedrockSetupFormTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the form holds the key in its own field and a code field as long as a code, and hands back the code")
    @SuppressWarnings("unchecked")
    void theFormHoldsTheKeyAndTakesTheCode() {
        BedrockScreen forms = Mockito.mock(BedrockScreen.class);
        List<String> answered = new ArrayList<>();

        new BedrockSetupForm(forms, world.messages).show(steve, "GEZDGNBV", 6, answered::add);

        ArgumentCaptor<List<BedrockWidget>> widgets = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Consumer<Map<String, String>>> submit = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Runnable> close = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(forms)
                .sendCustomForm(
                        Mockito.eq((Player) steve),
                        Mockito.eq(Text.plain(world.messages.render(steve, Keys.SCREEN_FACTOR_TITLE))),
                        Mockito.eq(Text.plain(world.messages.render(steve, Keys.SCREEN_FACTOR_BODY))),
                        widgets.capture(),
                        submit.capture(),
                        close.capture());
        assertThat(widgets.getValue())
                .containsExactly(
                        new BedrockWidget.Input(
                                BedrockSetupForm.KEY,
                                Text.plain(world.messages.render(steve, Keys.SCREEN_FACTOR_KEY)),
                                "",
                                "GEZDGNBV"),
                        new BedrockWidget.Input(
                                BedrockSetupForm.CODE,
                                Text.plain(world.messages.render(steve, Keys.SCREEN_FACTOR_CODE)),
                                "000000",
                                ""));

        submit.getValue().accept(Map.of(BedrockSetupForm.KEY, "GEZDGNBV", BedrockSetupForm.CODE, "123456"));
        close.getValue().run();

        assertThat(answered).containsExactly("123456");
    }

    @Test
    @DisplayName("the backup codes come in a field a phone copies from, and closing the form is all it asks")
    @SuppressWarnings("unchecked")
    void theCodesComeInAField() {
        BedrockScreen forms = Mockito.mock(BedrockScreen.class);

        new BedrockSetupForm(forms, world.messages).codes(steve, List.of("ABCDE-FGHJK", "MNPQR-STUVW"));

        ArgumentCaptor<List<BedrockWidget>> widgets = ArgumentCaptor.forClass(List.class);
        Mockito.verify(forms)
                .sendCustomForm(
                        Mockito.eq((Player) steve),
                        Mockito.eq(Text.plain(world.messages.render(steve, Keys.SCREEN_BACKUP_TITLE))),
                        Mockito.eq(Text.plain(world.messages.render(steve, Keys.SCREEN_BACKUP_BODY))),
                        widgets.capture(),
                        Mockito.any(Consumer.class),
                        Mockito.any(Runnable.class));
        assertThat(widgets.getValue())
                .containsExactly(new BedrockWidget.Input(
                        BedrockSetupForm.CODES,
                        Text.plain(world.messages.render(steve, Keys.SCREEN_BACKUP_CODES)),
                        "",
                        "ABCDE-FGHJK MNPQR-STUVW"));
    }
}
