package com.uxplima.auth.application.factor;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
 * an app; a remembered session leads to it unless the operator lets sessions skip it; a premium handshake and
 * Floodgate's word skip it while the operator counts them as proof. A new account, staff's word and the proxy's
 * word never ask: the proxy asked already.
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
    public static final SecondStep NONE = new SecondStep(account -> false, () -> new Settings(true, true, 1));

    private final Predicate<UUID> hasApp;
    private final Supplier<Settings> settings;

    public SecondStep(Factors factors, Supplier<Settings> settings) {
        this(account -> factors.app(account).isPresent(), settings);
        Objects.requireNonNull(factors, "factors");
    }

    private SecondStep(Predicate<UUID> hasApp, Supplier<Settings> settings) {
        this.hasApp = hasApp;
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Whether a first step that passed this way leads to a code for this account. Reads the table. */
    public boolean asks(Account account, AuthMethod method) {
        Objects.requireNonNull(account, "account");
        Settings now = settings.get();
        boolean byWay =
                switch (Objects.requireNonNull(method, "method")) {
                    case PASSWORD -> true;
                    case SESSION -> !now.sessionsSkip();
                    case PREMIUM, BEDROCK -> !now.premiumSkips();
                    case REGISTERED, FORCED, NETWORK -> false;
                };
        return byWay && hasApp.test(account.id());
    }

    /**
     * Where the connection stands once its first step passed this way: waiting for a code, held to replace a
     * weak password, or logged in.
     */
    public AuthState after(Account account, AuthMethod method, @Nullable PasswordVerdict mustReplace) {
        if (asks(account, method)) {
            return new AuthState.SecondFactor(
                    account,
                    method,
                    Set.of(FactorKind.APP),
                    Attempts.fresh(settings.get().attempts()),
                    mustReplace);
        }
        return mustReplace == null
                ? new AuthState.Authenticated(account, method)
                : new AuthState.Replacing(account, mustReplace);
    }
}
