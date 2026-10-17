package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.network.Bulletins;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.network.NetworkSecret;
import com.uxplima.uxmlib.redis.RedisBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Bulletins on Redis: every member on the channel hears each once, and nothing unsigned or late is heard. */
final class RedisEventsTest {

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");
    private static final Bulletin ENDED = new Bulletin.SessionsEnded(UUID.randomUUID(), java.util.Optional.empty());

    /** A Redis in memory: a frame published on a channel reaches every subscriber of it, the publisher too. */
    private static final class Wire implements RedisBus {
        private final Map<String, List<Consumer<byte[]>>> channels = new HashMap<>();

        @Override
        public void publish(String channel, byte[] frame) {
            channels.getOrDefault(channel, List.of()).forEach(subscriber -> subscriber.accept(frame));
        }

        @Override
        public void subscribe(String channel, Consumer<byte[]> onFrame) {
            channels.computeIfAbsent(channel, key -> new ArrayList<>()).add(onFrame);
        }

        @Override
        public void close() {}
    }

    private final Wire wire = new Wire();
    private final Claims claims = new Claims(NetworkSecret.parse("q".repeat(43)), Duration.ofSeconds(30));
    private final List<String> warnings = new ArrayList<>();
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private RedisEvents member(String channel, Claims sealing) {
        RedisEvents events =
                new RedisEvents(wire, channel, new Bulletins(sealing), () -> clock.instant(), warnings::add);
        events.start();
        return events;
    }

    @Test
    @DisplayName("a bulletin reaches every member on the channel once, its publisher too, and no other channel")
    void aBulletinReachesEveryMemberOnce() {
        RedisEvents proxy = member("uxmauth", claims);
        RedisEvents server = member("uxmauth", claims);
        RedisEvents elsewhere = member("other", claims);
        List<Bulletin> onProxy = new ArrayList<>();
        List<Bulletin> onServer = new ArrayList<>();
        List<Bulletin> onElsewhere = new ArrayList<>();
        proxy.listen(onProxy::add);
        server.listen(onServer::add);
        elsewhere.listen(onElsewhere::add);

        server.publish(ENDED);

        assertThat(onServer).containsExactly(ENDED);
        assertThat(onProxy).containsExactly(ENDED);
        assertThat(onElsewhere).isEmpty();
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("a frame sealed with another secret, or made up, is dropped with a warning")
    void anUnsignedFrameIsDropped() {
        RedisEvents server = member("uxmauth", claims);
        Bulletins forger = new Bulletins(new Claims(NetworkSecret.parse("r".repeat(43)), Duration.ofSeconds(30)));
        List<Bulletin> heard = new ArrayList<>();
        server.listen(heard::add);

        wire.publish(
                "uxmauth",
                forger.seal(new Bulletins.Sent(ENDED, UUID.randomUUID(), NOW)).getBytes(StandardCharsets.UTF_8));
        wire.publish("uxmauth", "hello".getBytes(StandardCharsets.UTF_8));

        assertThat(heard).isEmpty();
        assertThat(warnings).hasSize(2);
    }

    @Test
    @DisplayName("a bulletin that arrives after the fresh time is dropped")
    void aLateBulletinIsDropped() {
        RedisEvents sender = member("uxmauth", claims);
        List<Bulletin> heard = new ArrayList<>();
        Instant[] at = {NOW};
        RedisEvents late = new RedisEvents(wire, "uxmauth", new Bulletins(claims), () -> at[0], warnings::add);
        late.start();
        late.listen(heard::add);
        at[0] = NOW.plusSeconds(31);

        sender.publish(ENDED);

        assertThat(heard).isEmpty();
        assertThat(warnings).hasSize(1);
    }
}
