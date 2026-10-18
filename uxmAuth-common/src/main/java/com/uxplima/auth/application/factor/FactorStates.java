package com.uxplima.auth.application.factor;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.mail.EmailAddress;

/**
 * Whether an account has a second factor, an app or a mailed code, known in memory for the players logged in here,
 * so a placeholder answers without the database. A login reads it; every change made through the watched tables,
 * by the player or by staff, reads it again for an account already known.
 */
public final class FactorStates {

    private final Factors factors;
    private final Emails emails;
    private final Map<UUID, Boolean> known = new ConcurrentHashMap<>();

    /** @param factors the table as it is, and {@code emails} too, which this reads */
    public FactorStates(Factors factors, Emails emails) {
        this.factors = Objects.requireNonNull(factors, "factors");
        this.emails = Objects.requireNonNull(emails, "emails");
    }

    /** Whether the account has a second factor, when it is known here. Reads memory only. */
    public Optional<Boolean> on(UUID account) {
        return Optional.ofNullable(known.get(Objects.requireNonNull(account, "account")));
    }

    /** Reads whether the account has a second factor, for a login. Blocks on the database. */
    public void refresh(UUID account) {
        known.put(Objects.requireNonNull(account, "account"), read(account));
    }

    /** The player of this account left: nothing more is kept of it. */
    public void forget(UUID account) {
        known.remove(Objects.requireNonNull(account, "account"));
    }

    private boolean read(UUID account) {
        return factors.app(account).isPresent() || emails.mailsLoginCode(account);
    }

    /** A change to the account: read again, if it is known here. */
    private void changed(UUID account) {
        known.computeIfPresent(account, (id, before) -> read(id));
    }

    /** This table, telling every change of an app to what is known. */
    public Factors watching(Factors table) {
        return new WatchedFactors(Objects.requireNonNull(table, "table"), this::changed);
    }

    /** This table, telling every change of an address or of the mailed code to what is known. */
    public Emails watching(Emails table) {
        return new WatchedEmails(Objects.requireNonNull(table, "table"), this::changed);
    }

    private record WatchedFactors(Factors table, Consumer<UUID> changed) implements Factors {
        @Override
        public Optional<App> app(UUID account) {
            return table.app(account);
        }

        @Override
        public void setApp(UUID account, TotpSecret secret) {
            table.setApp(account, secret);
            changed.accept(account);
        }

        @Override
        public void removeApp(UUID account) {
            table.removeApp(account);
            changed.accept(account);
        }

        @Override
        public boolean useStep(UUID account, long step) {
            return table.useStep(account, step);
        }

        @Override
        public List<PasswordHash> backupCodes(UUID account) {
            return table.backupCodes(account);
        }

        @Override
        public void setBackupCodes(UUID account, List<PasswordHash> codes) {
            table.setBackupCodes(account, codes);
        }

        @Override
        public boolean useBackupCode(UUID account, PasswordHash code) {
            return table.useBackupCode(account, code);
        }
    }

    private record WatchedEmails(Emails table, Consumer<UUID> changed) implements Emails {
        @Override
        public Optional<EmailAddress> address(UUID account) {
            return table.address(account);
        }

        @Override
        public void setAddress(UUID account, EmailAddress address) {
            table.setAddress(account, address);
            changed.accept(account);
        }

        @Override
        public void removeAddress(UUID account) {
            table.removeAddress(account);
            changed.accept(account);
        }

        @Override
        public boolean mailsLoginCode(UUID account) {
            return table.mailsLoginCode(account);
        }

        @Override
        public void setMailsLoginCode(UUID account, boolean on) {
            table.setMailsLoginCode(account, on);
            changed.accept(account);
        }

        @Override
        public Optional<Code> code(UUID account, Purpose purpose) {
            return table.code(account, purpose);
        }

        @Override
        public Optional<Code> changeCode(UUID account, Purpose purpose, UnaryOperator<Optional<Code>> change) {
            return table.changeCode(account, purpose, change);
        }
    }
}
