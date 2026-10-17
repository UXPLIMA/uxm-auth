package com.uxplima.auth.paper.adapter.inbound.screen;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;

import net.kyori.adventure.text.Component;

import com.destroystokyo.paper.ClientOption;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.hold.Welcomes;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The dialog before the world. A client that waits is shown its screen while it is still connecting, and the
 * thread Paper gives this event is held until the client answers, leaves, or runs out of time. See ADR 0005.
 *
 * <p>The wait always ends: a close completes it at once and the timeout from config.conf completes it at the
 * latest. A client too old to draw a dialog is not held here; the world holds it, with the commands.
 */
public final class ConfigurationGate implements Listener {

    /** How a wait ended. */
    public enum Outcome {
        IN,
        WORLD,
        KICKED,
        CLOSED,
        TIMEOUT
    }

    /** One client waiting here: the connection it waits on, its reader, and the end of its wait. */
    record Waiting(
            PlayerConfigurationConnection connection,
            ConnectingAudience reader,
            long opening,
            AuthState state,
            CompletableFuture<Outcome> done) {}

    private static final Duration SAFETY = Duration.ofSeconds(5);

    private final Sessions sessions;
    private final Supplier<ScreenSettings> settings;
    private final Supplier<AuthSettings.Timeouts> timeouts;
    private final AuthScreens screens;
    private final ScreenAnswers answers;
    private final ScreenShower shower;
    private final Messages messages;
    private final Predicate<UUID> showsDialogs;
    private final Predicate<String> everyoneHolds;
    private final Welcomes welcomes;
    private final Logger logger;
    private final Map<UUID, Waiting> waiting = new ConcurrentHashMap<>();
    private final Set<UUID> toTheWorld = ConcurrentHashMap.newKeySet();

    /**
     * @param showsDialogs whether the client under this id can draw a dialog
     * @param everyoneHolds whether a permission is held by everyone, the only answer a client with no player has
     */
    public ConfigurationGate(
            Sessions sessions,
            Supplier<ScreenSettings> settings,
            Supplier<AuthSettings.Timeouts> timeouts,
            AuthScreens screens,
            ScreenAnswers answers,
            ScreenShower shower,
            Messages messages,
            Predicate<UUID> showsDialogs,
            Predicate<String> everyoneHolds,
            Welcomes welcomes,
            Logger logger) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
        this.screens = Objects.requireNonNull(screens, "screens");
        this.answers = Objects.requireNonNull(answers, "answers");
        this.shower = Objects.requireNonNull(shower, "shower");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.showsDialogs = Objects.requireNonNull(showsDialogs, "showsDialogs");
        this.everyoneHolds = Objects.requireNonNull(everyoneHolds, "everyoneHolds");
        this.welcomes = Objects.requireNonNull(welcomes, "welcomes");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        PlayerConfigurationConnection connection = event.getConnection();
        Optional<CompletableFuture<Outcome>> wait = open(connection);
        if (wait.isEmpty()) {
            return;
        }
        Outcome outcome = await(wait.get());
        if (outcome == Outcome.TIMEOUT) {
            connection.disconnect(messages.render(reader(connection), Keys.KICK_TIMEOUT));
        }
    }

    @EventHandler
    public void onClose(PlayerConnectionCloseEvent event) {
        UUID id = event.getPlayerUniqueId();
        Waiting entry = waiting.get(id);
        // The event names an id, not a connection. A second connection refused at the door closes under the
        // same id while the first still waits, so only a wait whose own connection is gone ends here.
        if (entry != null && entry.connection().isConnected()) {
            return;
        }
        toTheWorld.remove(id);
        if (entry != null && waiting.remove(id, entry)) {
            entry.done().complete(Outcome.CLOSED);
            welcomes.forget(id);
        }
    }

    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        if (PaperScreens.NAMESPACE.equals(event.getIdentifier().namespace())
                && event.getCommonConnection() instanceof PlayerConfigurationConnection connection) {
            CompletableFuture<Void> unused = answer(
                    connection,
                    event.getIdentifier().value(),
                    ScreenClickListener.answerOf(event.getDialogResponseView()));
        }
    }

    /** Whether this connection was let into the world without logging in: an old client, or one that left. */
    public boolean sentToTheWorld(UUID connection) {
        return toTheWorld.contains(Objects.requireNonNull(connection, "connection"));
    }

    /** The address of a connection waiting here under this id, if one is. */
    public Optional<InetAddress> waitingFrom(UUID connection) {
        Waiting entry = waiting.get(Objects.requireNonNull(connection, "connection"));
        InetSocketAddress address = entry == null ? null : entry.connection().getClientAddress();
        return Optional.ofNullable(address == null ? null : address.getAddress());
    }

    /** Shows the screen and starts the wait; empty when this connection does not wait here. */
    Optional<CompletableFuture<Outcome>> open(PlayerConfigurationConnection connection) {
        Objects.requireNonNull(connection, "connection");
        UUID id = connection.getProfile().getId();
        if (id == null || settings.get().waitingRoom() != ScreenSettings.WaitingRoom.DIALOG) {
            return Optional.empty();
        }
        Sessions.Held held = sessions.held(id).orElse(null);
        if (held == null || held.state() instanceof AuthState.Authenticated) {
            return Optional.empty();
        }
        AuthState state = held.state();
        if (!showsDialogs.test(id)) {
            toTheWorld.add(id);
            return Optional.empty();
        }
        CompletableFuture<Outcome> done = new CompletableFuture<>();
        Waiting entry = new Waiting(connection, reader(connection), held.generation(), state, done);
        // Two connections under one id can pass the door at the same moment; the first one waits here.
        if (waiting.putIfAbsent(id, entry) != null) {
            connection.disconnect(messages.render(entry.reader(), Keys.KICK_ALREADY_ONLINE));
            return Optional.empty();
        }
        CompletableFuture<Outcome> unused = done.whenComplete((outcome, failure) -> waiting.remove(id, entry));
        done.completeOnTimeout(Outcome.TIMEOUT, limit(state).toMillis(), TimeUnit.MILLISECONDS);
        shower.show(entry.reader(), firstScreen(entry));
        return Optional.of(done);
    }

    /** Runs one press from a client still connecting; a press from any other connection is thrown away. */
    CompletableFuture<Void> answer(
            PlayerConfigurationConnection connection, String action, ScreenAnswers.Answer answer) {
        UUID id = connection.getProfile().getId();
        Waiting entry = id == null ? null : waiting.get(id);
        if (entry == null || entry.connection() != connection) {
            return CompletableFuture.completedFuture(null);
        }
        return answers.answer(new ConnectingGuest(entry, this), action, answer);
    }

    void letIn(Waiting entry, AuthSettings.Moment moment) {
        welcomes.keep(entry.reader().id(), moment);
        entry.done().complete(Outcome.IN);
    }

    void release(Waiting entry) {
        toTheWorld.add(entry.reader().id());
        entry.done().complete(Outcome.WORLD);
    }

    void kicked(Waiting entry) {
        entry.done().complete(Outcome.KICKED);
    }

    boolean everyoneHolds(String permission) {
        return everyoneHolds.test(permission);
    }

    void showAgain(Waiting entry, Component reason) {
        shower.show(
                entry.reader(),
                entry.state() instanceof AuthState.Unregistered
                        ? screens.register(entry.reader(), reason)
                        : screens.login(entry.reader(), reason));
    }

    Messages messages() {
        return messages;
    }

    Logger logger() {
        return logger;
    }

    // The timeout completes the future on its own; the bound here only guards against that ever failing.
    private Outcome await(CompletableFuture<Outcome> done) {
        AuthSettings.Timeouts now = timeouts.get();
        long bound = Math.max(now.register().toMillis(), now.login().toMillis()) + SAFETY.toMillis();
        try {
            return done.get(bound, TimeUnit.MILLISECONDS);
        } catch (TimeoutException late) {
            return Outcome.TIMEOUT;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return Outcome.CLOSED;
        } catch (ExecutionException failed) {
            logger.log(Level.WARNING, "The dialog before the world ended with an error", failed.getCause());
            return Outcome.CLOSED;
        }
    }

    private DialogSpec firstScreen(Waiting entry) {
        return entry.state() instanceof AuthState.Unregistered
                ? screens.register(entry.reader(), null)
                : screens.login(entry.reader(), null);
    }

    private Duration limit(AuthState state) {
        AuthSettings.Timeouts now = timeouts.get();
        return state instanceof AuthState.Unregistered ? now.register() : now.login();
    }

    private static ConnectingAudience reader(PlayerConfigurationConnection connection) {
        UUID id = Objects.requireNonNull(connection.getProfile().getId(), "id");
        return new ConnectingAudience(id, connection.getAudience(), clientLocale(connection));
    }

    // The client writes its language as en_us; a locale reads it as en-US.
    private static @Nullable Locale clientLocale(PlayerConfigurationConnection connection) {
        String written = connection.getClientOption(ClientOption.LOCALE);
        return written == null || written.isBlank() ? null : Locale.forLanguageTag(written.replace('_', '-'));
    }
}
