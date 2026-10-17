package com.uxplima.auth.adapter.outbound;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.network.Bulletins;
import com.uxplima.uxmlib.redis.LettuceRedisBus;
import io.lettuce.core.RedisClient;
import org.jspecify.annotations.Nullable;

/** Opens the bulletins of a member: on Redis when the settings name one that answers, and on itself otherwise. */
public final class NetworkBulletins {

    private NetworkBulletins() {}

    /**
     * The bulletins of this member.
     *
     * @param warn where a member says what it could not do; the address is never in it, because it may carry the
     *     Redis password
     */
    public static NetworkEvents open(AuthSettings.Redis redis, Bulletins bulletins, Consumer<String> warn) {
        Objects.requireNonNull(redis, "redis");
        Objects.requireNonNull(bulletins, "bulletins");
        Objects.requireNonNull(warn, "warn");
        if (!redis.on()) {
            return new LocalEvents();
        }
        @Nullable RedisClient client = null;
        try {
            client = RedisClient.create(redis.uri());
            RedisEvents events =
                    new RedisEvents(new LettuceRedisBus(client, warn), redis.channel(), bulletins, Instant::now, warn);
            events.start();
            return events;
        } catch (RuntimeException unreachable) {
            if (client != null) {
                client.shutdown();
            }
            warn.accept("The Redis in network.redis.uri does not answer ("
                    + unreachable.getClass().getSimpleName()
                    + "). Until it does and this plugin starts again, what happens here is heard only here.");
            return new LocalEvents();
        }
    }
}
