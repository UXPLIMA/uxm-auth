package com.uxplima.auth.paper.adapter.inbound.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.migrate.MoveDatabase;
import com.uxplima.auth.application.port.DatabaseCopy;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The console moves the database; a player never can, and the console hears what came of it. */
final class DatabaseDeskTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private ConsoleCommandSenderMock console;
    private final List<String> logged = new ArrayList<>();
    private final List<Runnable> waiting = new ArrayList<>();
    private final AtomicInteger opened = new AtomicInteger();
    private Map<String, Long> target = Map.of("uxmauth_account", 0L, "uxmauth_meta", 0L);
    private int online;
    private boolean configured = true;
    private boolean held;

    private final DatabaseCopy copy = new DatabaseCopy() {
        @Override
        public Map<String, Long> sourceRows() {
            return Map.of("uxmauth_account", 3L, "uxmauth_meta", 2L);
        }

        @Override
        public Map<String, Long> targetRows() {
            return target;
        }

        @Override
        public Map<String, Long> copy() {
            return sourceRows();
        }

        @Override
        public void close() {}
    };

    private DatabaseDesk desk;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        console = server.getConsoleSender();
        MoveDatabase moves = new MoveDatabase(() -> online, () -> {
            opened.incrementAndGet();
            return configured ? Optional.of(copy) : Optional.empty();
        });
        desk = new DatabaseDesk(
                moves,
                work -> {
                    if (held) {
                        waiting.add(work);
                    } else {
                        work.run();
                    }
                },
                world.replies,
                world.messages,
                logged::add);
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a player, even an operator, is refused and nothing is opened")
    void aPlayerIsRefused() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.setOp(true);

        world.scheduler.settle(desk.move(staff));

        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        staff, Keys.DATABASE_CONSOLE_ONLY, Text.placeholder("command", "database move")));
        assertThat(opened).hasValue(0);
    }

    @Test
    @DisplayName("the console is told the move started and then what it copied, and the console log keeps a line")
    void theConsoleMoves() {
        world.scheduler.settle(desk.move(console));

        assertThat(console.nextComponentMessage())
                .isEqualTo(world.messages.render(console, Keys.DATABASE_MOVE_STARTED));
        assertThat(console.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        console,
                        Keys.DATABASE_MOVE_DONE,
                        Text.placeholder("rows", "5"),
                        Text.placeholder("tables", "2")));
        assertThat(logged).singleElement().asString().contains("5 rows").contains("2 tables");
    }

    @Test
    @DisplayName("each refusal reaches the console in its own words")
    void refusalsAreSaid() {
        online = 2;
        world.scheduler.settle(desk.move(console));
        console.nextComponentMessage();
        assertThat(console.nextComponentMessage())
                .isEqualTo(world.messages.render(console, Keys.DATABASE_MOVE_PLAYERS_ONLINE));

        online = 0;
        configured = false;
        world.scheduler.settle(desk.move(console));
        console.nextComponentMessage();
        assertThat(console.nextComponentMessage())
                .isEqualTo(world.messages.render(console, Keys.DATABASE_MOVE_NO_TARGET));

        configured = true;
        target = Map.of("uxmauth_account", 1L);
        world.scheduler.settle(desk.move(console));
        console.nextComponentMessage();
        assertThat(console.nextComponentMessage())
                .isEqualTo(world.messages.render(console, Keys.DATABASE_MOVE_TARGET_FULL));
        assertThat(logged).isEmpty();
    }

    @Test
    @DisplayName("a second move while one runs is refused, and the next after it is let through")
    void oneMoveAtATime() {
        held = true;
        CompletableFuture<Void> first = desk.move(console);
        console.nextComponentMessage();

        world.scheduler.settle(desk.move(console));
        assertThat(console.nextComponentMessage())
                .isEqualTo(world.messages.render(console, Keys.DATABASE_MOVE_RUNNING));

        waiting.forEach(Runnable::run);
        world.scheduler.settle(first);
        held = false;
        world.scheduler.settle(desk.move(console));
        assertThat(opened).hasValue(2);
    }
}
