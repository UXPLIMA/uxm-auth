package com.uxplima.auth.velocity.adapter.outbound.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import com.uxplima.auth.api.AccountType;
import com.uxplima.auth.api.LoginWay;
import com.uxplima.auth.api.velocity.event.BanEvent;
import com.uxplima.auth.api.velocity.event.FailedLoginEvent;
import com.uxplima.auth.api.velocity.event.LoginEvent;
import com.uxplima.auth.api.velocity.event.PreLoginEvent;
import com.uxplima.auth.api.velocity.event.PreRegisterEvent;
import com.uxplima.auth.api.velocity.event.RegisterEvent;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.velocity.adapter.inbound.ProxyAuthApi;
import com.uxplima.auth.velocity.adapter.inbound.ProxySessions;
import com.velocitypowered.api.proxy.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The proxy fires its events for what happens at the proxy, and answers the API from its own sessions. */
final class VelocityEventsTest {

    private final UUID id = UUID.randomUUID();
    private final Player steve = mock(Player.class);
    private final List<Object> fired = new CopyOnWriteArrayList<>();
    private volatile String refusal = "";
    private final VelocityEvents events = new VelocityEvents(
            asked -> asked.equals(id) ? Optional.of(steve) : Optional.empty(), new VelocityEvents.Bus() {
                @Override
                public CompletableFuture<?> ask(Object event) {
                    tell(event);
                    if (!refusal.isEmpty() && event instanceof PreLoginEvent pre) {
                        pre.refuse(refusal);
                    }
                    if (!refusal.isEmpty() && event instanceof PreRegisterEvent pre) {
                        pre.refuse(refusal);
                    }
                    return CompletableFuture.completedFuture(event);
                }

                @Override
                public void tell(Object event) {
                    fired.add(event);
                }
            });

    @Test
    @DisplayName("before a login or a registration the listeners are asked, and a refusal comes back")
    void theListenersAreAsked() {
        assertThat(events.beforeLogin(id)).isEmpty();
        refusal = "Closed.";
        assertThat(events.beforeLogin(id)).contains("Closed.");
        assertThat(events.beforeRegister(id)).contains("Closed.");
        assertThat(events.beforeLogin(UUID.randomUUID())).as("nobody here").isEmpty();

        assertThat(fired).hasSize(3);
    }

    @Test
    @DisplayName("a wrong password, a ban and a login fire with their facts; a registration fires both")
    void eventsFire() {
        when(steve.getUniqueId()).thenReturn(id);
        events.wrongPassword(id, 2);
        events.banned("203.0.113.7", Duration.ofMinutes(5));
        events.loggedIn(id, AuthMethod.REGISTERED);
        events.loggedIn(id, AuthMethod.PREMIUM);
        events.passwordChanged(id, new PlayerName("Steve"));
        events.unregistered(id, new PlayerName("Steve"));
        events.kindSwitched(id, new PlayerName("Steve"), AccountKind.PREMIUM);

        assertThat(fired)
                .containsExactly(
                        new FailedLoginEvent(steve, 2),
                        new BanEvent("203.0.113.7", Duration.ofMinutes(5)),
                        new RegisterEvent(steve),
                        new LoginEvent(steve, LoginWay.REGISTERED),
                        new LoginEvent(steve, LoginWay.PREMIUM));
    }

    @Test
    @DisplayName("the API answers from the proxy's sessions: in or not, the way, and the kind the way proves")
    void theApiAnswers() {
        ProxySessions sessions = new ProxySessions();
        ProxyAuthApi api = new ProxyAuthApi(sessions);
        UUID premium = UUID.randomUUID();
        UUID remembered = UUID.randomUUID();
        sessions.loggedIn(id, AuthMethod.PASSWORD);
        sessions.loggedIn(premium, AuthMethod.PREMIUM);
        sessions.loggedIn(remembered, AuthMethod.SESSION);

        assertThat(api.isAuthenticated(id)).isTrue();
        assertThat(api.method(id)).contains(LoginWay.PASSWORD);
        assertThat(api.kind(id)).contains(AccountType.CRACKED);
        assertThat(api.kind(premium)).contains(AccountType.PREMIUM);
        assertThat(api.kind(remembered))
                .as("a session proves no kind at the proxy")
                .isEmpty();
        assertThat(api.isAuthenticated(UUID.randomUUID())).isFalse();
        assertThat(api.method(UUID.randomUUID())).isEmpty();
    }
}
