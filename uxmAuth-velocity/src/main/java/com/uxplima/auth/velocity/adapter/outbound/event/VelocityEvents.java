package com.uxplima.auth.velocity.adapter.outbound.event;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.uxplima.auth.api.velocity.event.BanEvent;
import com.uxplima.auth.api.velocity.event.FailedLoginEvent;
import com.uxplima.auth.api.velocity.event.LoginEvent;
import com.uxplima.auth.api.velocity.event.PreLoginEvent;
import com.uxplima.auth.api.velocity.event.PreRegisterEvent;
import com.uxplima.auth.api.velocity.event.RegisterEvent;
import com.uxplima.auth.application.SessionsAuthApi;
import com.uxplima.auth.application.port.AuthEvents;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import com.velocitypowered.api.proxy.Player;

/**
 * Fires the API's Velocity events for what happens at the proxy. The two asked before a login or a registration
 * are waited for, on the lane that asks them; the rest are handed over and not waited for. A password change, a
 * removal and a kind switch happen on a server and fire there, so they fire nothing here.
 */
public final class VelocityEvents implements AuthEvents {

    /** The proxy's event bus. */
    public interface Bus {
        /** Hands the event over; done when every listener has seen it. */
        CompletableFuture<?> ask(Object event);

        /** Hands the event over and waits for nobody. */
        void tell(Object event);
    }

    private final Function<UUID, Optional<Player>> online;
    private final Bus bus;

    /**
     * @param online the player of this id on the proxy, when there is one
     * @param bus hands an event to the proxy's listeners
     */
    public VelocityEvents(Function<UUID, Optional<Player>> online, Bus bus) {
        this.online = Objects.requireNonNull(online, "online");
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    @Override
    public Optional<String> beforeLogin(UUID connection) {
        return online.apply(Objects.requireNonNull(connection, "connection")).flatMap(player -> {
            PreLoginEvent event = new PreLoginEvent(player);
            bus.ask(event).join();
            return event.refusal();
        });
    }

    @Override
    public Optional<String> beforeRegister(UUID connection) {
        return online.apply(Objects.requireNonNull(connection, "connection")).flatMap(player -> {
            PreRegisterEvent event = new PreRegisterEvent(player);
            bus.ask(event).join();
            return event.refusal();
        });
    }

    @Override
    public void wrongPassword(UUID connection, int attemptsLeft) {
        online.apply(Objects.requireNonNull(connection, "connection"))
                .ifPresent(player -> bus.tell(new FailedLoginEvent(player, attemptsLeft)));
    }

    @Override
    public void passwordChanged(UUID account, PlayerName name) {}

    @Override
    public void unregistered(UUID account, PlayerName name) {}

    @Override
    public void kindSwitched(UUID account, PlayerName name, AccountKind to) {}

    /** An address is banned at the proxy after its wrong passwords. */
    public void banned(String address, Duration length) {
        bus.tell(new BanEvent(address, length));
    }

    /** The player is logged in at the proxy, this way, whoever decided it; a registration fires both. */
    public void loggedIn(UUID id, AuthMethod method) {
        Objects.requireNonNull(method, "method");
        online.apply(Objects.requireNonNull(id, "id")).ifPresent(player -> {
            if (method == AuthMethod.REGISTERED) {
                bus.tell(new RegisterEvent(player));
            }
            bus.tell(new LoginEvent(player, SessionsAuthApi.way(method)));
        });
    }
}
