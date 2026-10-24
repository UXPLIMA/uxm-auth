package com.uxplima.auth.velocity.adapter.inbound;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.uxplima.auth.adapter.outbound.DoorWords;
import com.uxplima.auth.application.factor.SecondStep;
import com.uxplima.auth.application.protection.Admission;
import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.premium.Refusal;
import com.uxplima.auth.domain.state.AuthMethod;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import org.slf4j.Logger;

/**
 * Turns the door's answers into what Velocity does with a connection: online mode for a name the door asks,
 * offline mode for one it lets through, the profile the backends receive, and a refusal with its reason.
 *
 * <p>The door may wait on the database and the web, so every step that asks it runs as an asynchronous event
 * task, off the thread that reads the network. It runs late, after the plugins that decide a connection for
 * themselves: a connection already turned away, or already put in offline mode as Floodgate does a Bedrock
 * player, is left as they left it.
 */
public final class DoorListener {

    /** Below every plugin that leaves the default, so a decision another plugin made is seen. */
    private static final short LATE = -1000;

    private final Function<UUID, Optional<Account>> accounts;
    private final Remember remember;
    private final Admission admission;
    private final ProxyDoor door;
    private final ProxySessions sessions;
    private final DoorWords words;
    private final Logger logger;
    private final SecondStep secondStep;

    /**
     * The connections the waiting world settled before their login, by their address and port, which no other
     * connection shares. Their login keeps what the world settled; any other login is settled afresh.
     */
    private final Set<InetSocketAddress> settledEarly = ConcurrentHashMap.newKeySet();

    /**
     * @param admission the rules a connection meets before its name is read
     * @param remember the sessions that let a player back from the same address in without the password
     * @param accounts the account under an id, which a session is kept by
     */
    public DoorListener(
            ProxyDoor door,
            Admission admission,
            Remember remember,
            Function<UUID, Optional<Account>> accounts,
            ProxySessions sessions,
            DoorWords words,
            Logger logger) {
        this(door, admission, remember, accounts, sessions, words, logger, SecondStep.NONE);
    }

    /** @param secondStep whether a session or a premium name lets the player in, or only past the password */
    public DoorListener(
            ProxyDoor door,
            Admission admission,
            Remember remember,
            Function<UUID, Optional<Account>> accounts,
            ProxySessions sessions,
            DoorWords words,
            Logger logger,
            SecondStep secondStep) {
        this.door = Objects.requireNonNull(door, "door");
        this.admission = Objects.requireNonNull(admission, "admission");
        this.remember = Objects.requireNonNull(remember, "remember");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.words = Objects.requireNonNull(words, "words");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.secondStep = Objects.requireNonNull(secondStep, "secondStep");
    }

    @Subscribe(priority = LATE)
    public EventTask onPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> preLogin(event));
    }

    @Subscribe(priority = LATE)
    public EventTask onProfile(GameProfileRequestEvent event) {
        return EventTask.async(() -> profile(event));
    }

    /** Off the event thread: a remembered session is read from the database. */
    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        return EventTask.async(() -> login(event));
    }

    @Subscribe
    public void onLeft(DisconnectEvent event) {
        left(event);
    }

    void preLogin(PreLoginEvent event) {
        PreLoginEvent.PreLoginComponentResult before = event.getResult();
        if (!before.isAllowed()) {
            return;
        }
        String host = event.getConnection()
                .getVirtualHost()
                .map(InetSocketAddress::getHostString)
                .orElse("");
        if (ruledOut(event, host)) {
            return;
        }
        if (before.isForceOfflineMode()) {
            door.untouched(event.getConnection().getRemoteAddress());
            return;
        }
        decide(event, host);
    }

    /** What the door makes of the name: Mojang's proof, the password, or a refusal. */
    private void decide(PreLoginEvent event, String host) {
        try {
            event.setResult(
                    switch (door.preLogin(
                            event.getUsername(), event.getConnection().getRemoteAddress(), host)) {
                        case ProxyDoor.Gate.Online online -> PreLoginEvent.PreLoginComponentResult.forceOnlineMode();
                        case ProxyDoor.Gate.Offline offline -> PreLoginEvent.PreLoginComponentResult.forceOfflineMode();
                        case ProxyDoor.Gate.Refused refused ->
                            PreLoginEvent.PreLoginComponentResult.denied(
                                    words.refusal(refused.why(), refused.movedTo()));
                        case ProxyDoor.Gate.BadName bad ->
                            PreLoginEvent.PreLoginComponentResult.denied(words.name(bad.verdict()));
                    });
        } catch (RuntimeException failure) {
            // Nobody is let through a door that cannot answer: the name might be a paid owner's.
            logger.warn(
                    "Could not decide how {} joins; the connection is turned away.",
                    printable(event.getUsername()),
                    failure);
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    words.refusal(Refusal.LOOKUP_FAILED, Optional.empty())));
        }
    }

    /** Whether one of the door's rules turned the connection away; it reads the address, never the name. */
    private boolean ruledOut(PreLoginEvent event, String host) {
        Optional<Admission.Refused> ruled = admission.decide(new Admission.Arrival(
                event.getConnection().getRemoteAddress().getAddress().getHostAddress(), host));
        ruled.ifPresent(
                refused -> event.setResult(PreLoginEvent.PreLoginComponentResult.denied(words.admission(refused))));
        return ruled.isPresent();
    }

    void profile(GameProfileRequestEvent event) {
        InetSocketAddress remote = event.getConnection().getRemoteAddress();
        try {
            event.setGameProfile(door.profile(event.getGameProfile(), event.isOnlineMode(), remote));
        } catch (RuntimeException failure) {
            logger.warn(
                    "Could not make the profile of {}; the connection is turned away.",
                    printable(event.getUsername()),
                    failure);
            door.failed(remote);
        }
    }

    /**
     * Only a connection the door saw through to its profile, or one another plugin decided, logs in. One the door
     * put in online mode and never saw proved, one it lost track of, and one it refused are turned away.
     */
    void login(LoginEvent event) {
        Player player = event.getPlayer();
        Optional<Refusal> refusal =
                switch (door.arrival(player.getRemoteAddress()).orElse(null)) {
                    case ProxyDoor.Arrival.Usual usual -> {
                        if (!settledEarly.remove(player.getRemoteAddress())) {
                            waitOrResume(player.getUniqueId(), player.getUsername(), player, usual);
                        }
                        yield Optional.empty();
                    }
                    case ProxyDoor.Arrival.Premium premium -> {
                        passed(
                                player.getUniqueId(),
                                premium.state().account(),
                                premium.state().method());
                        yield Optional.empty();
                    }
                    case ProxyDoor.Arrival.Untouched untouched -> {
                        sessions.untouched(player.getUniqueId());
                        yield Optional.empty();
                    }
                    case ProxyDoor.Arrival.Refused refused -> Optional.of(refused.why());
                    case ProxyDoor.Arrival.Asked asked -> Optional.of(Refusal.LOOKUP_FAILED);
                    case null -> Optional.of(Refusal.LOOKUP_FAILED);
                };
        refusal.ifPresent(
                why -> event.setResult(ResultedEvent.ComponentResult.denied(words.refusal(why, Optional.empty()))));
    }

    /**
     * LimboAPI asks whether to hold a player before the proxy's profile and login events reach any other plugin, so
     * the door settles a player it let through offline here, under the id and the name the profile will carry. The
     * login after keeps what was settled, a login in the world among it.
     *
     * @return the profile the player waits under, or empty when they do not wait at the proxy
     */
    public Optional<GameProfile> settleEarly(Player player) {
        InetSocketAddress remote = Objects.requireNonNull(player, "player").getRemoteAddress();
        if (!(door.arrival(remote).orElse(null) instanceof ProxyDoor.Arrival.Usual usual)) {
            return Optional.empty();
        }
        GameProfile profile = door.profile(player.getGameProfile(), false, remote);
        waitOrResume(profile.getId(), profile.getName(), player, usual);
        settledEarly.add(remote);
        return sessions.waitsAtTheProxy(profile.getId()) ? Optional.of(profile) : Optional.empty();
    }

    /** A player with a password waits for it, unless a session from this address lets them in. */
    private void waitOrResume(UUID id, String name, Player player, ProxyDoor.Arrival.Usual usual) {
        sessions.waiting(id, new Claim.Waiting(usual.unverified(), usual.explain(), usual.marked()));
        String host =
                player.getVirtualHost().map(InetSocketAddress::getHostString).orElse("");
        // Only the account this very name holds, with a password, is let in by a session kept under its id.
        accounts.apply(id)
                .filter(account -> account.name().value().equals(name))
                .filter(account -> account.password() != null)
                .filter(account -> remember.resumes(
                        account.id(), player.getRemoteAddress().getAddress().getHostAddress(), host))
                .ifPresent(account -> passed(id, account, AuthMethod.SESSION));
    }

    /** A way in passed: the player is logged in, or past the password only, owing the app's code. */
    private void passed(UUID id, Account account, AuthMethod method) {
        if (secondStep.asks(account, method)) {
            sessions.code(id, method);
        } else {
            sessions.loggedIn(id, method);
        }
    }

    void left(DisconnectEvent event) {
        settledEarly.remove(event.getPlayer().getRemoteAddress());
        door.gone(event.getPlayer().getRemoteAddress());
        sessions.forget(event.getPlayer().getUniqueId());
    }

    /** A name as the console may print it: a control character could rewrite the line around it. */
    static String printable(String name) {
        return name.replaceAll("\\p{Cntrl}", "?");
    }

    ProxyDoor door() {
        return door;
    }
}
