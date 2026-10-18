package com.uxplima.auth.application.factor;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.factor.FactorKind;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import org.jspecify.annotations.Nullable;

/**
 * Whether a first step that passed is the whole login. A password always leads to the code where the account has
 * an app, or takes its code by mail; a remembered session leads to it unless the operator lets sessions skip it; a
 * premium handshake and Floodgate's word skip it while the operator counts them as proof. A new account, staff's
 * word and the proxy's word never ask: the proxy asked already. A step that takes a mailed code mails it as it
 * opens.
 */
public final class SecondStep {

    /**
     * What the operator chose.
     *
     * @param premiumSkips whether a premium handshake or Floodgate's word is proof enough
     * @param sessionsSkip whether a remembered session skips the code as it skips the password
     * @param attempts how many wrong codes a connection may type
     */
    public record Settings(boolean premiumSkips, boolean sessionsSkip, int attempts) {
        public Settings {
            if (attempts < 1) {
                throw new IllegalArgumentException("a second step needs at least one try, got " + attempts);
            }
        }
    }

    /** No factor table: nothing is ever asked, for a part built without one. */
    public static final SecondStep NONE =
            new SecondStep(account -> Set.of(), () -> new Settings(true, true, 1), account -> {});

    private final Function<UUID, Set<FactorKind>> kinds;
    private final Supplier<Settings> settings;
    private final Consumer<Account> mailing;

    /** @param mailing mails the login code to an account whose step takes one, as the step opens */
    public SecondStep(Factors factors, Emails emails, Supplier<Settings> settings, Consumer<Account> mailing) {
        this(
                kindsOf(Objects.requireNonNull(factors, "factors"), Objects.requireNonNull(emails, "emails")),
                settings,
                mailing);
    }

    private SecondStep(Function<UUID, Set<FactorKind>> kinds, Supplier<Settings> settings, Consumer<Account> mailing) {
        this.kinds = kinds;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.mailing = Objects.requireNonNull(mailing, "mailing");
    }

    private static Function<UUID, Set<FactorKind>> kindsOf(Factors factors, Emails emails) {
        return account -> {
            Set<FactorKind> found = EnumSet.noneOf(FactorKind.class);
            if (factors.app(account).isPresent()) {
                found.add(FactorKind.APP);
            }
            if (emails.mailsLoginCode(account)) {
                found.add(FactorKind.EMAIL);
            }
            return Set.copyOf(found);
        };
    }

    /** Whether a first step that passed this way leads to a code for this account. Reads the table. */
    public boolean asks(Account account, AuthMethod method) {
        Objects.requireNonNull(account, "account");
        return !skips(Objects.requireNonNull(method, "method"))
                && !kinds.apply(account.id()).isEmpty();
    }

    /** Whether this way skips the code by the operator's choice, before any table is read. */
    private boolean skips(AuthMethod method) {
        Settings now = settings.get();
        return switch (method) {
            case PASSWORD -> false;
            case SESSION -> now.sessionsSkip();
            case PREMIUM, BEDROCK -> now.premiumSkips();
            case REGISTERED, FORCED, NETWORK -> true;
        };
    }

    /**
     * Where the connection stands once its first step passed this way: waiting for a code, held to replace a
     * weak password, or logged in.
     */
    public AuthState after(Account account, AuthMethod method, @Nullable PasswordVerdict mustReplace) {
        Objects.requireNonNull(account, "account");
        Set<FactorKind> owed = skips(Objects.requireNonNull(method, "method")) ? Set.of() : kinds.apply(account.id());
        if (!owed.isEmpty()) {
            return waiting(account, method, owed, mustReplace);
        }
        return mustReplace == null
                ? new AuthState.Authenticated(account, method)
                : new AuthState.Replacing(account, mustReplace);
    }

    /**
     * Where a connection stands whose first step the proxy saw pass this way, and which it says owes the code: it
     * waits for the code, unless the account has no factor any more. The operator's skips were the proxy's to read.
     */
    public AuthState awaiting(Account account, AuthMethod method) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(method, "method");
        Set<FactorKind> owed = kinds.apply(account.id());
        return owed.isEmpty() ? new AuthState.Authenticated(account, method) : waiting(account, method, owed, null);
    }

    private AuthState waiting(
            Account account, AuthMethod method, Set<FactorKind> owed, @Nullable PasswordVerdict mustReplace) {
        if (owed.contains(FactorKind.EMAIL)) {
            mailing.accept(account);
        }
        return new AuthState.SecondFactor(
                account, method, owed, Attempts.fresh(settings.get().attempts()), mustReplace);
    }
}
