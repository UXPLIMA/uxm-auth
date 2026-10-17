package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.network.Bulletins;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.network.NetworkSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A member hears the network on Redis when the operator names one that answers, and only itself otherwise. */
final class NetworkBulletinsTest {

    private final Bulletins bulletins =
            new Bulletins(new Claims(NetworkSecret.parse("q".repeat(43)), Duration.ofSeconds(30)));
    private final List<String> warnings = new ArrayList<>();

    @Test
    @DisplayName("with no Redis address a member hears only itself, and nothing is said")
    void noAddressMeansAlone() {
        NetworkEvents events = NetworkBulletins.open(new AuthSettings.Redis("", "uxmauth"), bulletins, warnings::add);

        assertThat(events).isInstanceOf(LocalEvents.class);
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("a Redis that does not answer leaves the member alone, says so once, and never prints the address")
    void anUnreachableRedisMeansAlone() {
        NetworkEvents events = NetworkBulletins.open(
                new AuthSettings.Redis("redis://:hunter22@127.0.0.1:1", "uxmauth"), bulletins, warnings::add);

        assertThat(events).isInstanceOf(LocalEvents.class);
        assertThat(warnings)
                .singleElement()
                .satisfies(line -> assertThat(line)
                        .contains("network.redis.uri")
                        .doesNotContain("hunter22")
                        .doesNotContain("127.0.0.1"));
    }
}
