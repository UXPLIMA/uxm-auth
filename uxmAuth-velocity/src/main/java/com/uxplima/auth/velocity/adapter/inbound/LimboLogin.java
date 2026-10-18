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
import com.uxplima.auth.adapter.outbound.TimeWords;
import com.uxplima.auth.application.ChangePassword;
import com.uxplima.auth.application.ChangeResult;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.application.RegisterAccount;
import com.uxplima.auth.application.RegisterResult;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Claim;
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

    /** The words of /login, /register and /changepassword, as commands.conf names them. */
    public record Words(
            Supplier<Set<String>> loginLabels,
            Supplier<Set<String>> registerLabels,
            Supplier<Set<String>> changeLabels) {
        public Words {
            Objects.requireNonNull(loginLabels, "loginLabels");
            Objects.requireNonNull(registerLabels, "registerLabels");
            Objects.requireNonNull(changeLabels, "changeLabels");
        }
    }

    private final ReadTheName names;
    private final LogIn logIn;
    private final RegisterAccount register;
    private final ChangePassword change;
    private final Sessions sessions;
    private final Messages messages;
    private final TimeWords times;
    private final Words words;
    private final BooleanSupplier asksTwice;

    public LimboLogin(
            ReadTheName names,
            LogIn logIn,
            RegisterAccount register,
            ChangePassword change,
            Sessions sessions,
            Messages messages,
            Words words,
            BooleanSupplier asksTwice) {
        this.names = Objects.requireNonNull(names, "names");
        this.logIn = Objects.requireNonNull(logIn, "logIn");
        this.register = Objects.requireNonNull(register, "register");
        this.change = Objects.requireNonNull(change, "change");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.times = new TimeWords(messages);
        this.words = Objects.requireNonNull(words, "words");
        this.asksTwice = Objects.requireNonNull(asksTwice, "asksTwice");
    }

    /**
     * The player arrived in the waiting world: their wait opens under the door's flags, and the answer is what to
     * type, after why their last try failed when the door says they should read it.
     */
    public List<Component> arrive(Audience reader, UUID id, PlayerName name, Claim.Waiting door) {
        return arrive(reader, id, name, door, "");
    }

    /** The same, through the host the player typed, which a wrong password is counted with. */
    public List<Component> arrive(Audience reader, UUID id, PlayerName name, Claim.Waiting door, String host) {
        Objects.requireNonNull(reader, "reader");
        sessions.close(Objects.requireNonNull(id, "id"));
        Objects.requireNonNull(door, "door");
        AuthState state = names.waiting(Objects.requireNonNull(name, "name"));
        if (door.unverified() && state instanceof AuthState.Unregistered unregistered) {
            state = new AuthState.Unregistered(unregistered.name(), true);
        }
        sessions.open(id, state, UUID.randomUUID().toString(), Objects.requireNonNull(host, "host"));
        Component reminder = reminder(reader, id);
        return door.explain()
                ? List.of(messages.render(reader, Keys.LOGIN_HANDSHAKE_FAILED), reminder)
                : List.of(reminder);
    }

    /** What a line the player typed comes to. It may wait on the hashing lane and the database. */
    public CompletableFuture<Outcome> say(Audience reader, UUID id, PlayerName name, String line, String address) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(address, "address");
        List<String> typed =
                line.startsWith("/") ? Arrays.asList(line.substring(1).strip().split("\\s+")) : List.of();
        String label = typed.isEmpty() ? "" : typed.getFirst().toLowerCase(Locale.ROOT);
        List<String> rest = typed.isEmpty() ? List.of() : typed.subList(1, typed.size());
        if (words.loginLabels().get().contains(label) && rest.size() == 1) {
            return logIn.run(id, Password.of(rest.getFirst()), address)
                    .thenApply(result -> loggedIn(reader, id, result));
        }
        if (words.registerLabels().get().contains(label) && !rest.isEmpty()) {
            return registered(reader, id, name, rest, address);
        }
        if (words.changeLabels().get().contains(label) && replacing(id)) {
            return replaced(reader, id, rest);
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

    private CompletableFuture<Outcome> replaced(Audience reader, UUID id, List<String> passwords) {
        if (passwords.size() < 2) {
            return CompletableFuture.completedFuture(new Outcome.Stay(messages.render(reader, Keys.CHANGE_NEEDS_BOTH)));
        }
        return change.run(id, Password.of(passwords.getFirst()), Password.of(passwords.get(1)))
                .thenApply(result -> replaced(reader, id, result));
    }

    private Outcome replaced(Audience reader, UUID id, ChangeResult result) {
        return switch (result) {
            case ChangeResult.Replaced replaced -> new Outcome.In(AuthMethod.PASSWORD);
            case ChangeResult.Changed changed -> new Outcome.In(AuthMethod.PASSWORD);
            case ChangeResult.WrongOld wrong -> new Outcome.Stay(messages.render(reader, Keys.CHANGE_WRONG_OLD));
            case ChangeResult.Rejected rejected ->
                new Outcome.Stay(PasswordWords.reason(messages, reader, rejected.verdict())
                        .orElseGet(() -> reminder(reader, id)));
            case ChangeResult.NotLoggedIn notYet -> new Outcome.Stay(reminder(reader, id));
            case ChangeResult.Gone gone -> new Outcome.Stay(reminder(reader, id));
        };
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
            case LoginResult.Banned banned ->
                new Outcome.Out(messages.render(reader, Keys.KICK_BANNED, times.placeholder(reader, banned.lasts())));
            case LoginResult.MustReplace mustReplace ->
                new Outcome.Stay(PasswordWords.reason(messages, reader, mustReplace.why())
                        .map(why -> why.append(Component.newline()).append(reminder(reader, id)))
                        .orElseGet(() -> reminder(reader, id)));
            case LoginResult.CaptchaNeeded needed -> new Outcome.Stay(messages.render(reader, Keys.CAPTCHA_NEEDED));
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
            case RegisterResult.TooManyAccounts tooMany ->
                new Outcome.Stay(messages.render(reader, Keys.REGISTER_TOO_MANY_ACCOUNTS));
            case RegisterResult.CaptchaNeeded needed -> new Outcome.Stay(messages.render(reader, Keys.CAPTCHA_NEEDED));
            case RegisterResult.AlreadyRegistered already ->
                new Outcome.Stay(messages.render(reader, Keys.REGISTER_ALREADY));
            case RegisterResult.Gone gone -> new Outcome.Stay(reminder(reader, id));
        };
    }

    /**
     * What the player should type now: /register before an account, /login after, and /changepassword once a
     * right password the rules outgrew holds them.
     */
    public Component reminder(Audience reader, UUID id) {
        Objects.requireNonNull(reader, "reader");
        if (replacing(id)) {
            return messages.render(reader, Keys.LOGIN_MUST_REPLACE);
        }
        return messages.render(reader, registered(id) ? Keys.REMINDER_LOGIN : Keys.REMINDER_REGISTER);
    }

    private boolean replacing(UUID id) {
        return sessions.state(id).filter(AuthState.Replacing.class::isInstance).isPresent();
    }

    /** Whether the waiting player has an account. */
    public boolean registered(UUID id) {
        return sessions.state(Objects.requireNonNull(id, "id"))
                .filter(AuthState.Registered.class::isInstance)
                .isPresent();
    }
}
