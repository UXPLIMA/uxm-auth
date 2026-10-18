package com.uxplima.auth.velocity.adapter.inbound;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
import com.uxplima.auth.application.factor.ConfirmFactor;
import com.uxplima.auth.application.factor.SecondStep;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.protection.Captchas;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.protection.CaptchaPicture;
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

        /** The player stays, reads this, and is shown this captcha on a map in the hand. */
        record Captcha(Component said, CaptchaPicture picture) implements Outcome {
            public Captcha {
                Objects.requireNonNull(said, "said");
                Objects.requireNonNull(picture, "picture");
            }
        }

        /** The player is sent away, for this reason. */
        record Out(Component reason) implements Outcome {
            public Out {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    /** The words of /login, /register, /changepassword, /captcha, /2fa and /recover, as commands.conf names them. */
    public record Words(
            Supplier<Set<String>> loginLabels,
            Supplier<Set<String>> registerLabels,
            Supplier<Set<String>> changeLabels,
            Supplier<Set<String>> captchaLabels,
            Supplier<Set<String>> factorLabels,
            Supplier<Set<String>> recoverLabels) {
        public Words {
            Objects.requireNonNull(loginLabels, "loginLabels");
            Objects.requireNonNull(registerLabels, "registerLabels");
            Objects.requireNonNull(changeLabels, "changeLabels");
            Objects.requireNonNull(captchaLabels, "captchaLabels");
            Objects.requireNonNull(factorLabels, "factorLabels");
            Objects.requireNonNull(recoverLabels, "recoverLabels");
        }

        /** The words, with no /recover, for a waiting world that mails nothing. */
        public Words(
                Supplier<Set<String>> loginLabels,
                Supplier<Set<String>> registerLabels,
                Supplier<Set<String>> changeLabels,
                Supplier<Set<String>> captchaLabels,
                Supplier<Set<String>> factorLabels) {
            this(loginLabels, registerLabels, changeLabels, captchaLabels, factorLabels, Set::of);
        }

        /** The words, with no /2fa, for a waiting world that asks no code. */
        public Words(
                Supplier<Set<String>> loginLabels,
                Supplier<Set<String>> registerLabels,
                Supplier<Set<String>> changeLabels,
                Supplier<Set<String>> captchaLabels) {
            this(loginLabels, registerLabels, changeLabels, captchaLabels, Set::of);
        }
    }

    /** Checks the code of the second step, off the thread the line arrived on: the factor table is read. */
    @FunctionalInterface
    public interface CodeCheck {
        /** No code is asked, for a waiting world built without a factor table. */
        CodeCheck NONE =
                (id, code, address) -> CompletableFuture.completedFuture(new ConfirmFactor.Result.NotWaiting());

        CompletableFuture<ConfirmFactor.Result> confirm(UUID connection, String code, String address);
    }

    /**
     * A forgotten password: the words typed after /recover, from this address. Nothing answered leaves the player
     * with what to type now.
     */
    @FunctionalInterface
    public interface Recover {
        /** No recovery, for a waiting world built without mail. */
        Recover NONE = (reader, id, typed, address) -> CompletableFuture.completedFuture(Optional.empty());

        CompletableFuture<Optional<Outcome>> answer(Audience reader, UUID id, List<String> typed, String address);
    }

    private final ReadTheName names;
    private final LogIn logIn;
    private final RegisterAccount register;
    private final ChangePassword change;
    private final Captchas captchas;
    private final Sessions sessions;
    private final Messages messages;
    private final TimeWords times;
    private final Words words;
    private final BooleanSupplier asksTwice;
    private final SecondStep secondStep;
    private final CodeCheck codes;
    private final Recover recover;

    public LimboLogin(
            ReadTheName names,
            LogIn logIn,
            RegisterAccount register,
            ChangePassword change,
            Captchas captchas,
            Sessions sessions,
            Messages messages,
            Words words,
            BooleanSupplier asksTwice) {
        this(
                names,
                logIn,
                register,
                change,
                captchas,
                sessions,
                messages,
                words,
                asksTwice,
                SecondStep.NONE,
                CodeCheck.NONE);
    }

    /**
     * @param secondStep where a player the door let past the password waits for the app's code
     * @param codes checks the code of the second step
     */
    public LimboLogin(
            ReadTheName names,
            LogIn logIn,
            RegisterAccount register,
            ChangePassword change,
            Captchas captchas,
            Sessions sessions,
            Messages messages,
            Words words,
            BooleanSupplier asksTwice,
            SecondStep secondStep,
            CodeCheck codes) {
        this(
                names,
                logIn,
                register,
                change,
                captchas,
                sessions,
                messages,
                words,
                asksTwice,
                secondStep,
                codes,
                Recover.NONE);
    }

    /** @param recover a forgotten password, by a code mailed to the linked address */
    public LimboLogin(
            ReadTheName names,
            LogIn logIn,
            RegisterAccount register,
            ChangePassword change,
            Captchas captchas,
            Sessions sessions,
            Messages messages,
            Words words,
            BooleanSupplier asksTwice,
            SecondStep secondStep,
            CodeCheck codes,
            Recover recover) {
        this.names = Objects.requireNonNull(names, "names");
        this.logIn = Objects.requireNonNull(logIn, "logIn");
        this.register = Objects.requireNonNull(register, "register");
        this.change = Objects.requireNonNull(change, "change");
        this.captchas = Objects.requireNonNull(captchas, "captchas");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.times = new TimeWords(messages);
        this.words = Objects.requireNonNull(words, "words");
        this.asksTwice = Objects.requireNonNull(asksTwice, "asksTwice");
        this.secondStep = Objects.requireNonNull(secondStep, "secondStep");
        this.codes = Objects.requireNonNull(codes, "codes");
        this.recover = Objects.requireNonNull(recover, "recover");
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
        return arrive(reader, id, name, door, host, Optional.empty());
    }

    /**
     * The same, for a player the door let past the password by this way, who owes only the app's code: the wait
     * opens on the code.
     */
    public List<Component> arrive(
            Audience reader, UUID id, PlayerName name, Claim.Waiting door, String host, Optional<AuthMethod> passed) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(passed, "passed");
        sessions.close(Objects.requireNonNull(id, "id"));
        Objects.requireNonNull(door, "door");
        AuthState state = names.waiting(Objects.requireNonNull(name, "name"));
        if (door.unverified() && state instanceof AuthState.Unregistered unregistered) {
            state = new AuthState.Unregistered(unregistered.name(), true);
        }
        if (passed.isPresent() && state instanceof AuthState.Registered registered) {
            AuthState owed = secondStep.awaiting(registered.account(), passed.get());
            state = owed instanceof AuthState.SecondFactor ? owed : state;
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
        if (words.captchaLabels().get().contains(label) && rest.size() == 1) {
            return CompletableFuture.completedFuture(answered(reader, id, rest.getFirst()));
        }
        return otherWords(reader, id, label, rest, address);
    }

    /** /changepassword, /2fa and /recover, and what to type now for any other line. */
    private CompletableFuture<Outcome> otherWords(
            Audience reader, UUID id, String label, List<String> rest, String address) {
        if (words.changeLabels().get().contains(label) && replacing(id)) {
            return replaced(reader, id, rest);
        }
        if (words.factorLabels().get().contains(label) && rest.size() == 1) {
            return coded(reader, id, rest.getFirst(), address);
        }
        if (words.recoverLabels().get().contains(label)) {
            return recover.answer(reader, id, rest, address)
                    .thenApply(answer -> answer.orElseGet(() -> new Outcome.Stay(reminder(reader, id))));
        }
        return CompletableFuture.completedFuture(new Outcome.Stay(reminder(reader, id)));
    }

    /** The code of the second step; a player no code is asked of is told it is not asked. */
    private CompletableFuture<Outcome> coded(Audience reader, UUID id, String code, String address) {
        return codes.confirm(id, code, address).thenApply(result -> coded(reader, id, result));
    }

    private Outcome coded(Audience reader, UUID id, ConfirmFactor.Result result) {
        return switch (result) {
            case ConfirmFactor.Result.LoggedIn in
            when in.state() instanceof AuthState.Authenticated done -> new Outcome.In(done.method());
            case ConfirmFactor.Result.LoggedIn in -> new Outcome.Stay(reminder(reader, id));
            case ConfirmFactor.Result.Wrong wrong ->
                new Outcome.Stay(messages.render(
                        reader,
                        Keys.FACTOR_LOGIN_WRONG,
                        Text.placeholder("left", String.valueOf(wrong.attemptsLeft()))));
            case ConfirmFactor.Result.TooManyAttempts tooMany ->
                new Outcome.Out(messages.render(reader, Keys.KICK_TOO_MANY_ATTEMPTS));
            case ConfirmFactor.Result.Banned banned ->
                new Outcome.Out(messages.render(reader, Keys.KICK_BANNED, times.placeholder(reader, banned.lasts())));
            case ConfirmFactor.Result.NotWaiting notWaiting ->
                new Outcome.Stay(messages.render(reader, Keys.FACTOR_NOTHING_ASKED));
            case ConfirmFactor.Result.Gone gone -> new Outcome.Stay(reminder(reader, id));
        };
    }

    /** The captcha that stands before what this player does next, drawn fresh, when one does. */
    public Optional<Outcome> standing(Audience reader, UUID id) {
        Objects.requireNonNull(reader, "reader");
        boolean stands =
                switch (sessions.state(Objects.requireNonNull(id, "id")).orElse(null)) {
                    case AuthState.Unregistered unregistered -> captchas.blocksRegistration(id);
                    case AuthState.Registered waiting ->
                        captchas.blocks(id, waiting.account().name());
                    case null, default -> false;
                };
        return stands ? Optional.of(captcha(reader, id, Keys.CAPTCHA_NEEDED)) : Optional.empty();
    }

    /** The player left the waiting world. */
    public void leave(UUID id) {
        sessions.close(Objects.requireNonNull(id, "id"));
        captchas.forget(id);
    }

    /** The answer to the latest captcha: the right code frees the way, any other draws a new one. */
    private Outcome answered(Audience reader, UUID id, String code) {
        if (captchas.code(id).isEmpty()) {
            return new Outcome.Stay(messages.render(reader, Keys.CAPTCHA_NOTHING));
        }
        if (!captchas.solve(id, code)) {
            return captcha(reader, id, Keys.CAPTCHA_WRONG);
        }
        return new Outcome.Stay(messages.render(reader, Keys.CAPTCHA_SOLVED)
                .append(Component.newline())
                .append(reminder(reader, id)));
    }

    private Outcome.Captcha captcha(Audience reader, UUID id, Keys why) {
        Component said = messages.render(reader, why)
                .append(Component.newline())
                .append(messages.render(reader, Keys.CAPTCHA_MAP));
        return new Outcome.Captcha(said, captchas.showPicture(id));
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
            case LoginResult.CaptchaNeeded needed -> captcha(reader, id, Keys.CAPTCHA_NEEDED);
            case LoginResult.CodeNeeded needed -> new Outcome.Stay(messages.render(reader, Keys.FACTOR_CODE_NEEDED));
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
            case RegisterResult.CaptchaNeeded needed -> captcha(reader, id, Keys.CAPTCHA_NEEDED);
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
        if (sessions.state(id).orElse(null) instanceof AuthState.SecondFactor) {
            return messages.render(reader, Keys.FACTOR_CODE_NEEDED);
        }
        return messages.render(reader, registered(id) ? Keys.REMINDER_LOGIN : Keys.REMINDER_REGISTER);
    }

    private boolean replacing(UUID id) {
        return sessions.state(id).filter(AuthState.Replacing.class::isInstance).isPresent();
    }

    /** Whether the waiting player has an account: they wait for its password, or for its code. */
    public boolean registered(UUID id) {
        return sessions.state(Objects.requireNonNull(id, "id"))
                .filter(state -> state instanceof AuthState.Registered || state instanceof AuthState.SecondFactor)
                .isPresent();
    }
}
