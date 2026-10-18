package com.uxplima.auth.paper.adapter.outbound.event;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import com.uxplima.auth.api.AccountType;
import com.uxplima.auth.api.LoginWay;
import com.uxplima.auth.api.paper.event.BanEvent;
import com.uxplima.auth.api.paper.event.FailedLoginEvent;
import com.uxplima.auth.api.paper.event.KindSwitchEvent;
import com.uxplima.auth.api.paper.event.LoginEvent;
import com.uxplima.auth.api.paper.event.PasswordChangeEvent;
import com.uxplima.auth.api.paper.event.PreLoginEvent;
import com.uxplima.auth.api.paper.event.PreRegisterEvent;
import com.uxplima.auth.api.paper.event.RegisterEvent;
import com.uxplima.auth.api.paper.event.UnregisterEvent;
import com.uxplima.auth.application.port.AuthEvents;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import org.jspecify.annotations.Nullable;

/**
 * Fires the API's Bukkit events. The use cases call it off the server's threads, which is where an asynchronous
 * event belongs; a login is fired from the door, on the player's own thread. An event about a player who is not on
 * this server is not fired at all.
 */
public final class PaperEvents implements AuthEvents {

    private final Function<UUID, @Nullable Player> online;
    private final Consumer<Event> call;

    /**
     * @param online the player of this id on this server, when there is one
     * @param call hands an event to the server's listeners
     */
    public PaperEvents(Function<UUID, @Nullable Player> online, Consumer<Event> call) {
        this.online = Objects.requireNonNull(online, "online");
        this.call = Objects.requireNonNull(call, "call");
    }

    @Override
    public Optional<String> beforeLogin(UUID connection) {
        Player player = online.apply(Objects.requireNonNull(connection, "connection"));
        if (player == null) {
            return Optional.empty();
        }
        PreLoginEvent event = new PreLoginEvent(player);
        call.accept(event);
        return event.refusal();
    }

    @Override
    public Optional<String> beforeRegister(UUID connection) {
        Player player = online.apply(Objects.requireNonNull(connection, "connection"));
        if (player == null) {
            return Optional.empty();
        }
        PreRegisterEvent event = new PreRegisterEvent(player);
        call.accept(event);
        return event.refusal();
    }

    @Override
    public void wrongPassword(UUID connection, int attemptsLeft) {
        Player player = online.apply(Objects.requireNonNull(connection, "connection"));
        if (player != null) {
            call.accept(new FailedLoginEvent(player, attemptsLeft));
        }
    }

    @Override
    public void passwordChanged(UUID account, PlayerName name) {
        call.accept(new PasswordChangeEvent(account, name.value()));
    }

    @Override
    public void unregistered(UUID account, PlayerName name) {
        call.accept(new UnregisterEvent(account, name.value()));
    }

    @Override
    public void kindSwitched(UUID account, PlayerName name, AccountKind to) {
        call.accept(new KindSwitchEvent(account, name.value(), type(to)));
    }

    /** An address is banned after its wrong passwords. */
    public void banned(String address, Duration length) {
        call.accept(new BanEvent(address, length));
    }

    /** The player is logged in here, this way. Runs on the player's own thread; a registration fires both. */
    public void loggedIn(Player player, AuthMethod method) {
        Objects.requireNonNull(player, "player");
        if (Objects.requireNonNull(method, "method") == AuthMethod.REGISTERED) {
            call.accept(new RegisterEvent(player));
        }
        call.accept(new LoginEvent(player, way(method)));
    }

    /** The API's name of a way in. */
    public static LoginWay way(AuthMethod method) {
        return switch (method) {
            case PASSWORD -> LoginWay.PASSWORD;
            case REGISTERED -> LoginWay.REGISTERED;
            case PREMIUM -> LoginWay.PREMIUM;
            case BEDROCK -> LoginWay.BEDROCK;
            case SESSION -> LoginWay.SESSION;
            case FORCED -> LoginWay.FORCED;
            case NETWORK -> LoginWay.NETWORK;
        };
    }

    /** The API's name of a kind of account. */
    public static AccountType type(AccountKind kind) {
        return switch (kind) {
            case CRACKED -> AccountType.CRACKED;
            case PREMIUM -> AccountType.PREMIUM;
            case BEDROCK -> AccountType.BEDROCK;
        };
    }
}
