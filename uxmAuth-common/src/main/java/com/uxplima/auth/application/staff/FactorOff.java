package com.uxplima.auth.application.staff;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;

/**
 * Staff turn an account's second factor off, for a player who lost the phone and every backup code: the app,
 * the backup codes and the mailed code all go, and the next login asks the password only.
 */
public final class FactorOff {

    /** What turning off came to. */
    public enum Off {
        /** Every second factor of the account is off. */
        OFF,
        /** The account had none. */
        NONE,
        /** No account is under the name. */
        UNKNOWN
    }

    private final AccountRepository accounts;
    private final Factors factors;
    private final Emails emails;

    public FactorOff(AccountRepository accounts, Factors factors, Emails emails) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.factors = Objects.requireNonNull(factors, "factors");
        this.emails = Objects.requireNonNull(emails, "emails");
    }

    /** Turns off every second factor of the account under this name. Writes the tables. */
    public Off off(PlayerName name) {
        Optional<Account> account = accounts.findByName(Objects.requireNonNull(name, "name"));
        if (account.isEmpty()) {
            return Off.UNKNOWN;
        }
        UUID id = account.get().id();
        boolean had = factors.app(id).isPresent() || !factors.backupCodes(id).isEmpty() || emails.mailsLoginCode(id);
        factors.removeApp(id);
        factors.setBackupCodes(id, java.util.List.of());
        emails.setMailsLoginCode(id, false);
        return had ? Off.OFF : Off.NONE;
    }
}
