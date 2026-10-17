package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.adapter.outbound.SqlAccountRepository;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.NameVerdict;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.ConflictStrategy;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.Refusal;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.velocity.testing.ProxyWorld;
import com.velocitypowered.api.util.GameProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The proxy asks the same door a lone server asks, and Velocity's own online mode is the handshake. */
final class ProxyDoorTest {

    private static final UUID MOJANG = ProxyWorld.MOJANG;
    private static final InetSocketAddress FROM = new InetSocketAddress(InetAddress.getLoopbackAddress(), 40001);
    private static final InetSocketAddress AGAIN = new InetSocketAddress(InetAddress.getLoopbackAddress(), 40002);
    private static final String PLAY = ProxyWorld.PLAY;
    private static final List<GameProfile.Property> SKIN = List.of(new GameProfile.Property("textures", "skin", "sig"));

    private final ProxyWorld world;
    private final SqlAccountRepository accounts;
    private final ProxyWorld.MovingClock clock;

    ProxyDoorTest(@TempDir Path folder) {
        this.world = new ProxyWorld(folder);
        this.accounts = world.accounts;
        this.clock = world.clock;
    }

    private ProxyDoor door() {
        return world.door();
    }

    @AfterEach
    void close() {
        world.close();
    }

    private static GameProfile offlineProfile(String name) {
        return new GameProfile(UuidScheme.offline(new PlayerName(name)), name, List.of());
    }

    @Test
    @DisplayName(
            "a paid name nobody holds is put in online mode, and the proved player plays under Mojang's id and skin")
    void aPaidNameGoesOnline() {
        ProxyDoor door = door();

        assertThat(door.preLogin("Notch", FROM, PLAY)).isEqualTo(new ProxyDoor.Gate.Online());
        GameProfile played = door.profile(new GameProfile(MOJANG, "Notch", SKIN), true, FROM);

        assertThat(played.getId()).isEqualTo(MOJANG);
        assertThat(played.getName()).isEqualTo("Notch");
        assertThat(played.getProperties()).isEqualTo(SKIN);
        assertThat(door.arrival(FROM))
                .hasValueSatisfying(arrival -> assertThat(arrival)
                        .isInstanceOfSatisfying(
                                ProxyDoor.Arrival.Premium.class,
                                premium -> assertThat(premium.state().method()).isEqualTo(AuthMethod.PREMIUM)));
        assertThat(accounts.findByName(new PlayerName("Notch")))
                .hasValueSatisfying(account -> assertThat(account.kind()).isEqualTo(AccountKind.PREMIUM));
    }

    @Test
    @DisplayName("a name the web calls cracked stays in offline mode under the offline id")
    void aCrackedNameStaysOffline() {
        world.web = new PremiumAnswer.Cracked();
        ProxyDoor door = door();

        assertThat(door.preLogin("Steve", FROM, PLAY)).isEqualTo(new ProxyDoor.Gate.Offline());
        GameProfile played = door.profile(offlineProfile("Steve"), false, FROM);

        assertThat(played.getId()).isEqualTo(UuidScheme.offline(new PlayerName("Steve")));
        assertThat(door.arrival(FROM)).contains(new ProxyDoor.Arrival.Usual(false, false, false));
    }

    @Test
    @DisplayName("a connection asked and never proved failed: the next one from there is let in with the reason")
    void anUnprovedConnectionFailed() {
        ProxyDoor door = door();
        door.preLogin("Notch", FROM, PLAY);

        assertThat(door.preLogin("Notch", AGAIN, PLAY)).isEqualTo(new ProxyDoor.Gate.Offline());
        door.profile(offlineProfile("Notch"), false, AGAIN);

        assertThat(door.arrival(AGAIN)).contains(new ProxyDoor.Arrival.Usual(false, true, false));
    }

    @Test
    @DisplayName("a proved connection did not fail: the next one from there is asked again")
    void aProvedConnectionDidNotFail() {
        ProxyDoor door = door();
        door.preLogin("Notch", FROM, PLAY);
        door.profile(new GameProfile(MOJANG, "Notch", SKIN), true, FROM);

        assertThat(door.preLogin("Notch", AGAIN, PLAY)).isEqualTo(new ProxyDoor.Gate.Online());
    }

    @Test
    @DisplayName("through the cracked address of the domain rule a player plays as the marked name, under its own id")
    void theDomainRuleMarks() {
        world.rule = NameRule.DOMAIN;
        ProxyDoor door = door();

        assertThat(door.preLogin("Notch", FROM, PLAY)).isEqualTo(new ProxyDoor.Gate.Offline());
        GameProfile played = door.profile(offlineProfile("Notch"), false, FROM);

        assertThat(played.getName()).isEqualTo("Notch+");
        assertThat(played.getId()).isEqualTo(UuidScheme.offline(new PlayerName("Notch+")));
        assertThat(door.arrival(FROM)).contains(new ProxyDoor.Arrival.Usual(false, false, true));
    }

    @Test
    @DisplayName("a name the operator's rules refuse is turned away at the proxy, with the rule it broke")
    void aBadNameIsRefused() {
        world.rule = NameRule.DOMAIN;
        ProxyDoor door = door();

        assertThat(door.preLogin("SixteenCharNames", FROM, PLAY))
                .isEqualTo(new ProxyDoor.Gate.BadName(new NameVerdict.TooLong(15)));
        assertThat(door.preLogin("bad-name", AGAIN, "premium.example.org"))
                .isInstanceOfSatisfying(
                        ProxyDoor.Gate.BadName.class,
                        bad -> assertThat(bad.verdict()).isInstanceOf(NameVerdict.BadCharacters.class));
    }

    @Test
    @DisplayName("a premium account that failed lately is refused once with the reason, at the proxy")
    void aFailedPremiumAccountIsRefused() {
        ProxyDoor door = door();
        door.preLogin("Notch", FROM, PLAY);
        door.profile(new GameProfile(MOJANG, "Notch", SKIN), true, FROM);
        door.preLogin("Notch", AGAIN, PLAY);

        assertThat(door.preLogin("Notch", FROM, PLAY))
                .isEqualTo(new ProxyDoor.Gate.Refused(Refusal.PAID_NAME, Optional.empty()));
    }

    @Test
    @DisplayName("a proved owner the conflict rule stops is refused at the login, with the reason")
    void aBlockedOwnerIsRefusedAtTheLogin() {
        world.conflict = ConflictStrategy.BLOCK;
        world.rule = NameRule.STRICT;
        ProxyDoor door = door();
        AccountRepository.Insert unused = accounts.insert(new Account(
                UUID.randomUUID(),
                new PlayerName("Notch"),
                AccountKind.CRACKED,
                null,
                null,
                clock.instant(),
                "10.0.0.1",
                null,
                null));
        // Under the strict rule a paid name is asked whoever holds it.
        assertThat(door.preLogin("Notch", FROM, PLAY)).isEqualTo(new ProxyDoor.Gate.Online());
        door.profile(new GameProfile(MOJANG, "Notch", SKIN), true, FROM);

        assertThat(door.arrival(FROM)).contains(new ProxyDoor.Arrival.Refused(Refusal.NAME_DISPUTED));
    }

    @Test
    @DisplayName("under the random scheme a new cracked name plays under a fresh id, never the offline one")
    void theRandomSchemeGivesItsOwnId() {
        world.web = new PremiumAnswer.Cracked();
        world.scheme = UuidScheme.RANDOM;
        ProxyDoor door = door();
        door.preLogin("Steve", FROM, PLAY);

        GameProfile played = door.profile(offlineProfile("Steve"), false, FROM);

        assertThat(played.getId()).isNotEqualTo(UuidScheme.offline(new PlayerName("Steve")));
        assertThat(played.getName()).isEqualTo("Steve");
    }

    @Test
    @DisplayName("a player with an account plays under its id, whatever id the proxy made from the name")
    void anAccountKeepsItsId() {
        world.web = new PremiumAnswer.Cracked();
        UUID own = UUID.randomUUID();
        AccountRepository.Insert unused = accounts.insert(new Account(
                own,
                new PlayerName("Steve"),
                AccountKind.CRACKED,
                null,
                null,
                clock.instant(),
                "10.0.0.1",
                null,
                null));
        ProxyDoor door = door();
        door.preLogin("Steve", FROM, PLAY);

        assertThat(door.profile(offlineProfile("Steve"), false, FROM).getId()).isEqualTo(own);
    }

    @Test
    @DisplayName("what the proxy kept of a connection that never logged in is let go after a minute")
    void anAbandonedConnectionIsLetGo() {
        world.web = new PremiumAnswer.Cracked();
        ProxyDoor door = door();
        door.preLogin("Steve", FROM, PLAY);

        world.clock.pass(Duration.ofMinutes(2));
        door.preLogin("Alex", AGAIN, PLAY);

        assertThat(door.arrival(FROM)).isEmpty();
        assertThat(door.arrival(AGAIN)).isPresent();
    }

    @Test
    @DisplayName("what the proxy kept of a connection is gone once it leaves")
    void aGoneConnectionIsForgotten() {
        ProxyDoor door = door();
        door.preLogin("Notch", FROM, PLAY);
        door.profile(new GameProfile(MOJANG, "Notch", SKIN), true, FROM);

        door.gone(FROM);

        assertThat(door.arrival(FROM)).isEmpty();
    }
}
