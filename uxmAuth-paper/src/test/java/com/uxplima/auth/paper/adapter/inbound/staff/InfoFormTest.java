package com.uxplima.auth.paper.adapter.inbound.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.application.staff.AccountInfo;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.bedrock.BedrockButton;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The info view on Bedrock: one form with the lines the console reads, and a button for each other account. */
final class InfoFormTest {

    private static final Instant AT = Instant.parse("2026-10-18T09:05:00Z");

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock staff;
    private final Set<UUID> bedrock = new HashSet<>();
    private final Forms forms = new Forms();
    private final List<String> opened = new ArrayList<>();
    private InfoForm form;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        staff = server.addPlayer("Staff");
        form = new InfoForm(
                forms,
                bedrock::contains,
                world.messages,
                world.scheduler,
                (viewer, name) -> opened.add(viewer.getName() + " -> " + name));
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private AccountInfo.View steve() {
        world.registered(server.addPlayer("Steve"), "hunter22");
        world.registered(server.addPlayer("Alex"), "hunter22");
        for (String name : List.of("Steve", "Alex")) {
            world.history.record(
                    world.accounts
                            .findByName(new PlayerName(name))
                            .orElseThrow()
                            .id(),
                    new LoginHistory.Entry(AT, "203.0.113.7", AuthMethod.PASSWORD));
        }
        return world.accountInfo().of(new PlayerName("Steve")).orElseThrow();
    }

    @Test
    @DisplayName("a Java player is left to the window")
    void aJavaPlayerGetsNoForm() {
        assertThat(form.show(staff, steve(), List.of(Component.text("line")))).isFalse();
        assertThat(forms.shown).isZero();
    }

    @Test
    @DisplayName("a Bedrock player reads the lines in the form, and a tap on another account reads that one")
    void aBedrockPlayerGetsTheForm() {
        bedrock.add(staff.getUniqueId());

        assertThat(form.show(staff, steve(), List.of(Component.text("first line"), Component.text("second line"))))
                .isTrue();

        assertThat(forms.shown).isEqualTo(1);
        assertThat(forms.title).contains("Steve");
        assertThat(forms.content).isEqualTo("first line\nsecond line");
        assertThat(forms.buttons).extracting(BedrockButton::text).containsExactly("Alex");
        forms.select.accept(0);
        world.scheduler.drain();
        assertThat(opened).containsExactly("Staff -> Alex");
    }

    private static final class Forms implements BedrockScreen {
        int shown;
        String title = "";

        @Nullable String content;

        List<BedrockButton> buttons = List.of();
        IntConsumer select = index -> {};

        @Override
        public void sendSimpleForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockButton> buttons,
                IntConsumer onSelect) {
            shown++;
            this.title = title;
            this.content = content;
            this.buttons = buttons;
            this.select = onSelect;
        }

        @Override
        public void sendModalForm(
                Player player,
                String title,
                @Nullable String content,
                String button1,
                String button2,
                Runnable onButton1,
                Runnable onButton2) {}

        @Override
        public void sendInputForm(
                Player player,
                String title,
                String inputLabel,
                @Nullable String initial,
                Consumer<String> onSubmit,
                Runnable onClose) {}

        @Override
        public void sendCustomForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockWidget> widgets,
                Consumer<Map<String, String>> onSubmit,
                Runnable onClose) {}
    }
}
