package com.uxplima.auth.paper.adapter.inbound.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.perf.Timings;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Staff read the time spent on each kind of work, over the last minute and since start, one line a kind. */
final class PerfViewTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock staff;
    private long now = TimeUnit.HOURS.toNanos(1);
    private final Timings timings = new Timings(() -> now);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        staff = server.addPlayer("Staff");
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("each kind reads its count, average and worst in milliseconds, for the minute and since start")
    void eachKindIsShown() {
        timings.record(Timings.Kind.HASHING, TimeUnit.MICROSECONDS.toNanos(40_250));
        now += TimeUnit.SECONDS.toNanos(90);
        timings.record(Timings.Kind.HASHING, TimeUnit.MILLISECONDS.toNanos(60));

        new PerfView(timings, world.messages).show(staff);

        assertThat(staff.nextComponentMessage()).isEqualTo(world.messages.render(staff, Keys.PERF_HEADER));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        staff,
                        Keys.PERF_LINE,
                        Placeholder.component("kind", world.messages.render(staff, Keys.PERF_HASHING)),
                        Text.placeholder("minute-count", "1"),
                        Text.placeholder("minute-average", "60.0"),
                        Text.placeholder("minute-worst", "60.0"),
                        Text.placeholder("count", "2"),
                        Text.placeholder("average", "50.1"),
                        Text.placeholder("worst", "60.0")));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        staff,
                        Keys.PERF_LINE,
                        Placeholder.component("kind", world.messages.render(staff, Keys.PERF_DATABASE)),
                        Text.placeholder("minute-count", "0"),
                        Text.placeholder("minute-average", "0.0"),
                        Text.placeholder("minute-worst", "0.0"),
                        Text.placeholder("count", "0"),
                        Text.placeholder("average", "0.0"),
                        Text.placeholder("worst", "0.0")));
        assertThat(staff.nextComponentMessage()).isNotNull();
        assertThat(staff.nextComponentMessage()).isNull();
    }
}
