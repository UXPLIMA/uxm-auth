package com.uxplima.auth.application.mail;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.factor.FactorCheck;
import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.domain.mail.EmailAddress;

/**
 * The login code by mail, for a player who will not install an app. It goes on for an account with a linked address
 * and off only with a code that passes, and a wrong one counts against the address. The table is read here, so this
 * runs off the server thread.
 */
public final class MailFactor {

    /** What turning it on or off came to. */
    public sealed interface Outcome {
        /** It is on, and codes go to this address, shown masked. */
        record On(String masked) implements Outcome {}

        /** It is off. */
        record Off() implements Outcome {}

        /** It was on already. */
        record AlreadyOn() implements Outcome {}

        /** It is not on. */
        record NotOn() implements Outcome {}

        /** No address is linked to send it to. */
        record NoAddress() implements Outcome {}

        /** The code did not pass. */
        record Wrong() implements Outcome {}

        /** The wrong code banned the address it came from, for this long. */
        record Banned(Duration lasts) implements Outcome {}
    }

    /** Whether a code passes for the account, spending it when it does. */
    @FunctionalInterface
    public interface Proof {
        boolean passes(UUID account, String typed);
    }

    private final Emails emails;
    private final Proof proof;
    private final LogIn.Failures failures;

    public MailFactor(Emails emails, Proof proof, LogIn.Failures failures) {
        this.emails = Objects.requireNonNull(emails, "emails");
        this.proof = Objects.requireNonNull(proof, "proof");
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    /** A proof by any factor the account has, now. */
    public static Proof by(FactorCheck check, Supplier<Instant> clock) {
        Objects.requireNonNull(check, "check");
        Objects.requireNonNull(clock, "clock");
        return (account, typed) -> check.passes(account, typed, clock.get());
    }

    /** Turns the mailed code on for an account with a linked address. */
    public Outcome on(UUID account) {
        Objects.requireNonNull(account, "account");
        Optional<EmailAddress> linked = emails.address(account);
        if (linked.isEmpty()) {
            return new Outcome.NoAddress();
        }
        if (emails.mailsLoginCode(account)) {
            return new Outcome.AlreadyOn();
        }
        emails.setMailsLoginCode(account, true);
        return new Outcome.On(linked.get().masked());
    }

    /** Turns it off with a code that passes, typed from this address. */
    public Outcome off(UUID account, String typed, String address, String host) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(typed, "typed");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(host, "host");
        if (!emails.mailsLoginCode(account)) {
            return new Outcome.NotOn();
        }
        if (!proof.passes(account, typed)) {
            return failures.failed(address, host)
                    .<Outcome>map(Outcome.Banned::new)
                    .orElseGet(Outcome.Wrong::new);
        }
        emails.setMailsLoginCode(account, false);
        // A code out when it went off would otherwise still answer a login for its minutes.
        emails.changeCode(account, Emails.Purpose.LOGIN, before -> Optional.empty());
        return new Outcome.Off();
    }
}
