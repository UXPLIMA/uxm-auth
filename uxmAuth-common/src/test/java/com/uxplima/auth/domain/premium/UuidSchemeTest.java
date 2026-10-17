package com.uxplima.auth.domain.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The UUID a player plays under, for each of the four schemes and each kind of arrival. */
final class UuidSchemeTest {

    private static final PlayerName NOTCH = new PlayerName("Notch");
    private static final UUID MOJANG = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID OFFLINE = UUID.nameUUIDFromBytes("OfflinePlayer:Notch".getBytes(StandardCharsets.UTF_8));
    private static final UUID FIRST = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static Optional<Account> held() {
        return Optional.of(
                new Account(FIRST, NOTCH, AccountKind.CRACKED, null, null, Instant.EPOCH, "10.0.0.1", null, null));
    }

    @Test
    @DisplayName("the offline UUID is the one the server itself derives from the name")
    void theOfflineIdIsTheServers() {
        assertThat(UuidScheme.offline(NOTCH)).isEqualTo(OFFLINE);
    }

    @Test
    @DisplayName(
            "keep-first: a new premium player gets Mojang's, a new cracked one the offline one, an account its own")
    void keepFirst() {
        assertThat(UuidScheme.KEEP_FIRST.idFor(NOTCH, Optional.of(MOJANG), Optional.empty()))
                .isEqualTo(MOJANG);
        assertThat(UuidScheme.KEEP_FIRST.idFor(NOTCH, Optional.empty(), Optional.empty()))
                .isEqualTo(OFFLINE);
        assertThat(UuidScheme.KEEP_FIRST.idFor(NOTCH, Optional.of(MOJANG), held()))
                .isEqualTo(FIRST);
    }

    @Test
    @DisplayName("offline: everybody new gets the offline UUID, a paid account included")
    void offline() {
        assertThat(UuidScheme.OFFLINE.idFor(NOTCH, Optional.of(MOJANG), Optional.empty()))
                .isEqualTo(OFFLINE);
        assertThat(UuidScheme.OFFLINE.idFor(NOTCH, Optional.of(MOJANG), held())).isEqualTo(FIRST);
    }

    @Test
    @DisplayName("mojang: every premium player gets Mojang's, so a cracked account that turns premium moves to it")
    void mojang() {
        assertThat(UuidScheme.MOJANG.idFor(NOTCH, Optional.of(MOJANG), Optional.empty()))
                .isEqualTo(MOJANG);
        assertThat(UuidScheme.MOJANG.idFor(NOTCH, Optional.of(MOJANG), held())).isEqualTo(MOJANG);
        assertThat(UuidScheme.MOJANG.idFor(NOTCH, Optional.empty(), held())).isEqualTo(FIRST);
        assertThat(UuidScheme.MOJANG.idFor(NOTCH, Optional.empty(), Optional.empty()))
                .isEqualTo(OFFLINE);
    }

    @Test
    @DisplayName("random: a new player gets a fresh UUID nobody can derive, and an account keeps its own")
    void random() {
        UUID fresh = UuidScheme.RANDOM.idFor(NOTCH, Optional.of(MOJANG), Optional.empty());

        assertThat(fresh).isNotIn(MOJANG, OFFLINE);
        assertThat(UuidScheme.RANDOM.idFor(NOTCH, Optional.empty(), Optional.empty()))
                .isNotEqualTo(fresh);
        assertThat(UuidScheme.RANDOM.idFor(NOTCH, Optional.of(MOJANG), held())).isEqualTo(FIRST);
    }
}
