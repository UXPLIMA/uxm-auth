package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * A waiting player answers in chat what the commands ask: the password to log in, a new password and its repeat to
 * register, and the code of the second step. The commands still work beside it.
 *
 * <p>What a waiting player types is a password more often than not, so the line is taken at the lowest priority,
 * cancelled, and its viewers cleared, whatever came before: no player, no chat log and no plugin after this one
 * reads it.
 */
public final class ChatAnswers implements Listener {

    /** What the commands do with a typed answer. */
    public record Answers(
            BiFunction<Player, String, CompletableFuture<Void>> login,
            Register register,
            BiFunction<Player, String, CompletableFuture<Void>> code) {

        public Answers {
            Objects.requireNonNull(login, "login");
            Objects.requireNonNull(register, "register");
            Objects.requireNonNull(code, "code");
        }

        /** The answers the three commands give. */
        public static Answers of(LoginCommand login, RegisterCommand register, TwoFactorCommand factor) {
            Objects.requireNonNull(login, "login");
            Objects.requireNonNull(register, "register");
            Objects.requireNonNull(factor, "factor");
            return new Answers(login::attempt, register::attempt, factor::answer);
        }
    }

    /** A registration from a password and its repeat. */
    @FunctionalInterface
    public interface Register {
        CompletableFuture<Void> attempt(Player player, String password, String repeat);
    }

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Sessions sessions;
    private final BooleanSupplier on;
    private final BooleanSupplier asksTwice;
    private final Answers answers;
    private final Messages messages;

    /** The first password of a registration that waits for its repeat; changed only through compute. */
    private final Map<UUID, String> firsts = new ConcurrentHashMap<>();

    /**
     * @param on whether chat answers at all, which the operator chooses
     * @param asksTwice whether a registration asks its password twice
     */
    public ChatAnswers(
            Sessions sessions, BooleanSupplier on, BooleanSupplier asksTwice, Answers answers, Messages messages) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.on = Objects.requireNonNull(on, "on");
        this.asksTwice = Objects.requireNonNull(asksTwice, "asksTwice");
        this.answers = Objects.requireNonNull(answers, "answers");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!asks(player)) {
            return;
        }
        event.setCancelled(true);
        event.viewers().clear();
        Optional<CompletableFuture<Void>> unused = take(player, PLAIN.serialize(event.message()));
    }

    /**
     * The older chat event, which Paper still fires for a plugin written against it. It never answers, the
     * modern one does; it only makes sure such a plugin does not read the line.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (asks(event.getPlayer())) {
            event.setCancelled(true);
            event.getRecipients().clear();
        }
    }

    /** Answers what the player's wait asks with this line; empty when chat does not answer for this player. */
    public Optional<CompletableFuture<Void>> take(Player player, String line) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(line, "line");
        if (!asks(player)) {
            return Optional.empty();
        }
        forgetTheGone();
        String typed = line.strip();
        AuthState state = sessions.state(player.getUniqueId()).orElseThrow();
        return Optional.of(
                switch (state) {
                    case AuthState.Unregistered unregistered -> register(player, typed);
                    case AuthState.Registered registered -> answers.login().apply(player, typed);
                    case AuthState.SecondFactor waiting -> answers.code().apply(player, typed);
                    default -> CompletableFuture.completedFuture(null);
                });
    }

    /** Whether chat answers for this player now: it is on, and the player waits here. */
    private boolean asks(Player player) {
        return on.getAsBoolean()
                && sessions.state(player.getUniqueId())
                        .filter(state -> !(state instanceof AuthState.Authenticated))
                        .isPresent();
    }

    private CompletableFuture<Void> register(Player player, String typed) {
        if (!asksTwice.getAsBoolean()) {
            return answers.register().attempt(player, typed, "");
        }
        AtomicReference<@Nullable String> first = new AtomicReference<>();
        firsts.compute(player.getUniqueId(), (key, before) -> {
            first.set(before);
            return before == null ? typed : null;
        });
        String earlier = first.get();
        if (earlier == null) {
            messages.send(player, Keys.CHAT_REGISTER_REPEAT);
            return CompletableFuture.completedFuture(null);
        }
        return answers.register().attempt(player, earlier, typed);
    }

    /** A first password whose player left goes with them. */
    private void forgetTheGone() {
        for (UUID id : firsts.keySet()) {
            firsts.compute(id, (key, first) -> sessions.state(key).isPresent() ? first : null);
        }
    }

    /** How many first passwords are kept, for a test that proves they are let go. */
    int waiting() {
        return firsts.size();
    }
}
