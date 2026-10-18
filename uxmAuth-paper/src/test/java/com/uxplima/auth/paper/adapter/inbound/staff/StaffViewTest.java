package com.uxplima.auth.paper.adapter.inbound.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** /uxmauth info and /uxmauth history: an account as staff read it, in lines of the catalogue. */
final class StaffViewTest {

    private static final Instant AT = Instant.parse("2026-10-18T09:05:00Z");

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock staff;
    private StaffView view;
    /** The window a player sender was shown. It answers windowOpens, and false leaves the player the lines. */
    private final List<String> shown = new ArrayList<>();

    private boolean windowOpens;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        staff = server.addPlayer("Staff");
        view = new StaffView(
                world.accountInfo(),
                world.lane,
                world.replies,
                world.messages,
                () -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC),
                (viewer, account, lines) -> {
                    shown.add(viewer.getName() + " " + account.account().name().value() + " " + lines.size());
                    return windowOpens;
                });
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private UUID steve() {
        world.registered(server.addPlayer("Steve"), "hunter22");
        return world.accounts.findByName(new PlayerName("Steve")).orElseThrow().id();
    }

    private List<String> said() {
        List<String> lines = new ArrayList<>();
        Component next;
        while ((next = staff.nextComponentMessage()) != null) {
            // The test catalogue has no styles, so its tags read as text; the words are what is checked.
            lines.add(PlainTextComponentSerializer.plainText()
                    .serialize(next)
                    .replaceAll("<(etag|tag):[^>]+> |<[a-z]+>", ""));
        }
        return lines;
    }

    @Test
    @DisplayName("info names the account, its kind, its last login and count, its factors, its address and alts")
    void infoReadsTheAccount() {
        UUID steve = steve();
        world.history.record(steve, new LoginHistory.Entry(AT, "203.0.113.7", AuthMethod.PASSWORD));
        world.registered(server.addPlayer("Alex"), "hunter22");
        world.history.record(
                world.accounts.findByName(new PlayerName("Alex")).orElseThrow().id(),
                new LoginHistory.Entry(AT, "203.0.113.7", AuthMethod.PASSWORD));
        world.factors.setApp(steve, new TotpSecret(new byte[20]));
        world.emails.setAddress(steve, EmailAddress.parse("steve@example.net").orElseThrow());

        world.scheduler.settle(view.info(staff, "steve"));

        List<String> lines = said();
        assertThat(lines.getFirst()).contains("Steve").contains("a password account");
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("1 logins kept"));
        assertThat(lines)
                .anySatisfy(line -> assertThat(line).contains("App: on").contains("Code by mail: off"));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("s***@e***.net"));
        assertThat(lines)
                .anySatisfy(line -> assertThat(line).contains("Other accounts").contains("Alex"));
        assertThat(String.join("\n", lines)).doesNotContain("steve@example.net");
    }

    @Test
    @DisplayName("history lists the latest logins newest first, with date, address and way in")
    void historyListsLogins() {
        UUID steve = steve();
        world.history.record(steve, new LoginHistory.Entry(AT, "203.0.113.7", AuthMethod.PASSWORD));
        world.history.record(steve, new LoginHistory.Entry(AT.plusSeconds(3600), "198.51.100.2", AuthMethod.SESSION));

        world.scheduler.settle(view.history(staff, "Steve"));

        assertThat(said())
                .containsSubsequence(
                        "2026-10-18 10:05 from 198.51.100.2, by session.",
                        "2026-10-18 09:05 from 203.0.113.7, by password.");
    }

    @Test
    @DisplayName("an unknown name says so, and an account with no login kept says that")
    void unknownAndEmpty() {
        world.scheduler.settle(view.info(staff, "Nobody"));
        assertThat(said()).singleElement().asString().contains("No account is under Nobody");

        steve();
        world.scheduler.settle(view.history(staff, "Steve"));
        assertThat(said()).anySatisfy(line -> assertThat(line).contains("No login of Steve is kept"));
    }

    @Test
    @DisplayName("a player is shown the window with the same lines; the console and a player without it read lines")
    void aPlayerGetsTheWindow() {
        steve();
        windowOpens = true;
        world.scheduler.settle(view.info(staff, "Steve"));
        assertThat(shown).containsExactly("Staff Steve 6");
        assertThat(said()).isEmpty();

        windowOpens = false;
        world.scheduler.settle(view.info(staff, "Steve"));
        assertThat(said()).hasSize(6);

        shown.clear();
        world.scheduler.settle(view.info(server.getConsoleSender(), "Steve"));
        assertThat(shown).as("the console has no window").isEmpty();
    }
}
