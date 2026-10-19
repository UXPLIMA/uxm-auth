package com.uxplima.auth.paper.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** A command whose work failed tells its player what they can do about it. */
final class RepliesTest {

    @TempDir
    Path folder;

    private AuthWorld world;
    private PlayerMock steve;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private void failWith(Throwable failure) {
        CompletableFuture<Void> work = CompletableFuture.failedFuture(new CompletionException(failure));
        world.scheduler.settle(world.replies.on(steve, work, unused -> {}));
    }

    @Test
    @DisplayName("a database that cannot be reached is said as such, as the door says it, not as an internal error")
    void anUnreachableDatabaseIsSaid() {
        failWith(new IllegalStateException("query failed", new SQLException("Connection refused")));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.COMMAND_STORAGE));
    }

    @Test
    @DisplayName("any other failure is still the internal error")
    void anyOtherFailureIsInternal() {
        failWith(new IllegalStateException("a defect"));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.COMMAND_INTERNAL_ERROR));
    }
}
