package com.uxplima.auth.application.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.port.AddressTallies;
import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.protection.AddressBans;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.protection.AddressRanges;
import com.uxplima.auth.domain.protection.AddressTally;
import com.uxplima.auth.domain.protection.BanSteps;
import com.uxplima.auth.domain.protection.SharedAddresses;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.testing.InMemoryLoginHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What staff hear: failed logins once a window, bans, an address many accounts use, and what staff did. */
final class AlertsTest {

    private static final Instant START = Instant.parse("2026-10-18T10:00:00Z");
    private static final String HOME = "203.0.113.7";
    private static final String PLAY = "play.example.org";
    private static final Alerts.Settings ALL = new Alerts.Settings(true, Duration.ofSeconds(60), true, 3, true);

    private final List<Bulletin> heard = new ArrayList<>();
    private final List<Runnable> queued = new ArrayList<>();
    private final NetworkEvents events = new NetworkEvents() {
        @Override
        public void publish(Bulletin bulletin) {
            heard.add(bulletin);
        }

        @Override
        public void listen(Consumer<Bulletin> listener) {}
    };
    private final LoginHistory history = new InMemoryLoginHistory();
    private final Map<String, AddressTally> kept = new ConcurrentHashMap<>();
    private final AddressBans bans = new AddressBans(
            new AddressTallies() {
                @Override
                public Optional<AddressTally> find(String key) {
                    return Optional.ofNullable(kept.get(key));
                }

                @Override
                public AddressTally change(String key, UnaryOperator<AddressTally> change) {
                    return kept.compute(key, (k, tally) -> change.apply(tally == null ? AddressTally.clean() : tally));
                }

                @Override
                public void clear(String key) {
                    kept.remove(key);
                }
            },
            () -> new BanSteps(List.of(Duration.ofMinutes(5)), 3, Duration.ofHours(24)),
            () -> new SharedAddresses(
                    new HostPatterns(List.of("*.tcpshield.com")), new AddressRanges(List.of("198.51.100.0/24"))),
            () -> this.now);
    private Alerts.Settings settings = ALL;
    private Instant now = START;
    private final Alerts alerts = new Alerts(() -> settings, history, events, queued::add, () -> now);

    /** Runs what the alerts handed off the calling thread, as the server's async pool would. */
    private List<Bulletin> sent() {
        assertThat(heard).as("nothing is published on the calling thread").isEmpty();
        queued.forEach(Runnable::run);
        queued.clear();
        List<Bulletin> out = List.copyOf(heard);
        heard.clear();
        return out;
    }

    @Test
    @DisplayName("a burst of wrong answers from one address is one line a window, and the ban that ends it is one more")
    void aBurstIsOneLineAWindow() {
        LogIn.Failures failures = alerts.watching(bans);

        assertThat(failures.failed(HOME, PLAY)).isEmpty();
        assertThat(failures.failed(HOME, PLAY)).isEmpty();
        assertThat(sent()).containsExactly(new Bulletin.FailedLogin(HOME));
        assertThat(failures.failed(HOME, PLAY)).contains(Duration.ofMinutes(5));
        assertThat(sent()).containsExactly(new Bulletin.Banned(HOME, Duration.ofMinutes(5)));

        now = START.plusSeconds(61);
        bans.unban(HOME);
        failures.failed(HOME, PLAY);
        failures.failed("2001:db8::1", PLAY);
        failures.failed("2001:db8::2", PLAY);
        assertThat(sent())
                .as("a new window opens a new line, and an IPv6 home is one address by its /64")
                .containsExactly(new Bulletin.FailedLogin(HOME), new Bulletin.FailedLogin("2001:db8::1"));
    }

    @Test
    @DisplayName("an address the filter shares, or none at all, raises nothing")
    void aSharedAddressRaisesNothing() {
        LogIn.Failures failures = alerts.watching(bans);

        failures.failed("198.51.100.20", "mc.tcpshield.com");
        failures.failed("", PLAY);
        assertThat(sent()).isEmpty();
        failures.failed("198.51.100.20", PLAY);
        assertThat(sent())
                .as("the filter's address with a host it does not answer for is an ordinary connection")
                .containsExactly(new Bulletin.FailedLogin("198.51.100.20"));
    }

    @Test
    @DisplayName("a new account on an address that reaches the threshold is told; one that used it before is not")
    void aSharedAddressIsTold() {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        history.record(steve, new LoginHistory.Entry(START, HOME, AuthMethod.PASSWORD));

        alerts.landed(alex, new PlayerName("Alex"), HOME);
        history.record(alex, new LoginHistory.Entry(START, HOME, AuthMethod.PASSWORD));
        assertThat(sent()).as("two accounts are below the threshold of three").isEmpty();

        alerts.landed(third, new PlayerName("Third"), HOME);
        history.record(third, new LoginHistory.Entry(START, HOME, AuthMethod.PASSWORD));
        alerts.landed(steve, new PlayerName("Steve"), HOME);
        alerts.landed(UUID.randomUUID(), new PlayerName("Nowhere"), "");
        assertThat(sent()).containsExactly(new Bulletin.SharedAddress(new PlayerName("Third"), HOME, 3));
    }

    @Test
    @DisplayName("what staff did is told with who did it, the console as nobody")
    void staffActionsAreTold() {
        alerts.staffActed(Optional.of(new PlayerName("Mod")), StaffAction.UNREGISTER, "Steve");
        alerts.staffActed(Optional.empty(), StaffAction.UNBAN, HOME);

        assertThat(sent())
                .containsExactly(
                        new Bulletin.StaffActed(Optional.of(new PlayerName("Mod")), "unregister", "Steve"),
                        new Bulletin.StaffActed(Optional.empty(), "unban", HOME));
    }

    @Test
    @DisplayName("each switch off sends its alert no more, and the others still go")
    void aSwitchOffSendsNothing() {
        UUID first = UUID.randomUUID();
        history.record(first, new LoginHistory.Entry(START, HOME, AuthMethod.PASSWORD));
        history.record(UUID.randomUUID(), new LoginHistory.Entry(START, HOME, AuthMethod.PASSWORD));
        LogIn.Failures failures = alerts.watching(bans);

        settings = new Alerts.Settings(false, Duration.ofSeconds(60), true, 0, false);
        failures.failed(HOME, PLAY);
        failures.failed(HOME, PLAY);
        alerts.landed(UUID.randomUUID(), new PlayerName("Third"), HOME);
        alerts.staffActed(Optional.empty(), StaffAction.UNBAN, HOME);
        assertThat(sent()).isEmpty();
        failures.failed(HOME, PLAY);
        assertThat(sent()).containsExactly(new Bulletin.Banned(HOME, Duration.ofMinutes(5)));

        settings = new Alerts.Settings(true, Duration.ofSeconds(60), false, 3, true);
        now = START.plus(Duration.ofHours(1));
        bans.unban(HOME);
        failures.failed(HOME, PLAY);
        failures.failed(HOME, PLAY);
        failures.failed(HOME, PLAY);
        assertThat(sent()).containsExactly(new Bulletin.FailedLogin(HOME));
    }

    @Test
    @DisplayName("a window lasts a second or more, and a threshold is off or two accounts or more")
    void settingsAreChecked() {
        assertThatThrownBy(() -> new Alerts.Settings(true, Duration.ZERO, true, 3, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Alerts.Settings(true, Duration.ofSeconds(60), true, 1, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Alerts.Settings(true, Duration.ofSeconds(60), true, -1, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Alerts.Settings(true, Duration.ofSeconds(60), true, 0, true).sharedAddressAccounts())
                .isZero();
    }
}
