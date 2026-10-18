package com.uxplima.auth.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.mail.EmailAddress;

/** Each account's confirmed address and the codes mailed to it, shared by a whole network. */
public interface Emails {

    /** What a mailed code is for; each account holds one of each at most. */
    enum Purpose {
        VERIFY,
        LOGIN,
        RECOVERY
    }

    /**
     * A code mailed to an address, kept as a hash.
     *
     * @param attemptsLeft how many more tries it takes
     */
    record Code(EmailAddress to, PasswordHash hash, Instant expires, int attemptsLeft, Instant sent) {
        public Code {
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(hash, "hash");
            Objects.requireNonNull(expires, "expires");
            Objects.requireNonNull(sent, "sent");
        }

        /** The same code with one try fewer. */
        public Code spent() {
            return new Code(to, hash, expires, attemptsLeft - 1, sent);
        }
    }

    /** The account's confirmed address, when it has one. */
    Optional<EmailAddress> address(UUID account);

    /** Keeps this confirmed address for the account, in place of any before it. */
    void setAddress(UUID account, EmailAddress address);

    /** The account no longer has an address. */
    void removeAddress(UUID account);

    /** The account's code of this purpose, when one is out. */
    Optional<Code> code(UUID account, Purpose purpose);

    /**
     * Changes the account's code of this purpose as one step across every server that shares the table, and
     * answers it after. The change sees the code as it is now and may run more than once; it must do nothing else.
     */
    Optional<Code> changeCode(UUID account, Purpose purpose, UnaryOperator<Optional<Code>> change);
}
