package com.uxplima.auth.velocity.adapter.inbound;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.PasswordWords;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.application.RegisterAccount;
import com.uxplima.auth.application.RegisterResult;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The password login of the proxy's waiting world. A player there has no server and no command tree: they
 * type /login or /register in chat, in any word commands.conf gives those commands, and the use cases a server
 * runs answer them.
 */
public final class LimboLogin {

    /** What a line the player typed comes to. */
    public sealed interface Outcome {

        /** The player stays and reads this. */
        record Stay(Component said) implements Outcome {
            public Stay {
                Objects.requireNonNull(said, "said");
            }
        }

        /** The player is in, by this method. */
        record In(AuthMethod method) implements Outcome {
            public In {
                Objects.requireNonNull(method, "method");
            }
        }

        /** The player is sent away, for this reason. */
        record Out(Component reason) implements Outcome {
            public Out {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    private final ReadTheName names;
    private final LogIn logIn;
    private final RegisterAccount register;
    private final Sessions sessions;
    private final Messages messages;
    private final Supplier<Set<String>> loginWords;
    private final Supplier<Set<String>> registerWords;
    private final BooleanSupplier asksTwice;

    public LimboLogin(
            ReadTheName names,
            LogIn logIn,
            RegisterAccount register,
            Sessions sessions,
            Messages messages,
            Supplier<Set<String>> loginWords,
            Supplier<Set<String>> registerWords,
            BooleanSupplier asksTwice) {
        this.names = Objects.requireNonNull(names, "names");
        this.logIn = Objects.requireNonNull(logIn, "logIn");
        this.register = Objects.requireNonNull(register, "register");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.loginWords = Objects.requireNonNull(loginWords, "loginWords");
        this.registerWords = Objects.requireNonNull(registerWords, "registerWords");
        this.asksTwice = Objects.requireNonNull(asksTwice, "asksTwice");
    }

    /** The player arrived in the waiting world: their wait opens, and the answer is what to type. */
    public Component arrive(Audience reader, UUID id, PlayerName name) {
        Objects.requireNonNull(reader, "reader");
        sessions.close(Objects.requireNonNull(id, "id"));
        sessions.open(id, names.waiting(Objects.requireNonNull(name, "name")));
        return reminder(reader, id);
    }

    /** What a line the player typed comes to. It may wait on the hashing lane and the database. */
    public CompletableFuture<Outcome> say(Audience reader, UUID id, PlayerName name, String line, String address) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(address, "address");
        List<String> words =
                line.startsWith("/") ? Arrays.asList(line.substring(1).strip().split("\\s+")) : List.of();
        String label = words.isEmpty() ? "" : words.getFirst().toLowerCase(Locale.ROOT);
        List<String> rest = words.isEmpty() ? List.of() : words.subList(1, words.size());
        if (loginWords.get().contains(label) && rest.size() == 1) {
            return logIn.run(id, Password.of(rest.getFirst()), address)
                    .thenApply(result -> loggedIn(reader, id, result));
        }
        if (registerWords.get().contains(label) && !rest.isEmpty()) {
            return registered(reader, id, name, rest, address);
        }
        return CompletableFuture.completedFuture(new Outcome.Stay(reminder(reader, id)));
    }

    /** The player left the waiting world. */
    public void leave(UUID id) {
        sessions.close(Objects.requireNonNull(id, "id"));
    }

    private CompletableFuture<Outcome> registered(
            Audience reader, UUID id, PlayerName name, List<String> passwords, String address) {
        boolean twice = asksTwice.getAsBoolean();
        if (twice && passwords.size() < 2) {
            return CompletableFuture.completedFuture(
                    new Outcome.Stay(messages.render(reader, Keys.REGISTER_CONFIRM_NEEDED)));
        }
        String confirm = twice ? passwords.get(1) : passwords.getFirst();
        return register.run(id, name, Password.of(passwords.getFirst()), Password.of(confirm), address)
                .thenApply(result -> registered(reader, id, result));
    }

    private Outcome loggedIn(Audience reader, UUID id, LoginResult result) {
        return switch (result) {
            case LoginResult.LoggedIn in -> new Outcome.In(AuthMethod.PASSWORD);
            case LoginResult.AlreadyLoggedIn already -> new Outcome.In(AuthMethod.PASSWORD);
            case LoginResult.Wrong wrong ->
                new Outcome.Stay(messages.render(
                        reader, Keys.LOGIN_WRONG, Text.placeholder("left", String.valueOf(wrong.attemptsLeft()))));
            case LoginResult.TooManyAttempts tooMany ->
                new Outcome.Out(messages.render(reader, Keys.KICK_TOO_MANY_ATTEMPTS));
            case LoginResult.NotRegistered notRegistered ->
                new Outcome.Stay(messages.render(reader, Keys.LOGIN_NOT_REGISTERED));
            case LoginResult.Gone gone -> new Outcome.Stay(reminder(reader, id));
        };
    }

    private Outcome registered(Audience reader, UUID id, RegisterResult result) {
        return switch (result) {
            case RegisterResult.Created created -> new Outcome.In(AuthMethod.REGISTERED);
            case RegisterResult.Mismatch mismatch -> new Outcome.Stay(messages.render(reader, Keys.REGISTER_MISMATCH));
            case RegisterResult.Rejected rejected ->
                new Outcome.Stay(PasswordWords.reason(messages, reader, rejected.verdict())
                        .orElseGet(() -> reminder(reader, id)));
            case RegisterResult.NameTaken taken ->
                new Outcome.Stay(messages.render(
                        reader,
                        Keys.REGISTER_NAME_TAKEN,
                        Text.placeholder("name", taken.stored().value())));
            case RegisterResult.AlreadyRegistered already ->
                new Outcome.Stay(messages.render(reader, Keys.REGISTER_ALREADY));
            case RegisterResult.Gone gone -> new Outcome.Stay(reminder(reader, id));
        };
    }

    /** What the player should type now: /register before an account, /login after. */
    private Component reminder(Audience reader, UUID id) {
        boolean registered = sessions.state(id)
                .filter(AuthState.Registered.class::isInstance)
                .isPresent();
        return messages.render(reader, registered ? Keys.REMINDER_LOGIN : Keys.REMINDER_REGISTER);
    }
}
