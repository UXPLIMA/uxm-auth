package com.uxplima.auth.application.factor;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.domain.factor.BackupCodes;
import com.uxplima.auth.domain.factor.Totp;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.password.Password;

/**
 * Whether a typed code answers an account's second factor: the app's code for now, taken once across every server,
 * the login code mailed to it, or one of the backup codes, each spent once. A backup code is checked against slow hashes, so this runs off the server
 * thread, on the lane the password hashes run on.
 */
public final class FactorCheck {

    /** The login code mailed to the account, taken once. */
    @FunctionalInterface
    public interface MailedCode {
        /** No mail: nothing is ever taken. */
        MailedCode NONE = (account, typed) -> false;

        /** Whether this is the code mailed to the account, which is spent when it is. */
        boolean takes(UUID account, String typed);
    }

    private final Factors factors;
    private final Hashing hashing;
    private final Supplier<Totp> totp;
    private final Supplier<BackupCodes> backup;
    private final MailedCode mailed;

    public FactorCheck(
            Factors factors, Hashing hashing, Supplier<Totp> totp, Supplier<BackupCodes> backup, MailedCode mailed) {
        this.factors = Objects.requireNonNull(factors, "factors");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.totp = Objects.requireNonNull(totp, "totp");
        this.backup = Objects.requireNonNull(backup, "backup");
        this.mailed = Objects.requireNonNull(mailed, "mailed");
    }

    /** Whether the code passes for the account; a code that passes is spent. */
    public boolean passes(UUID account, String typed, Instant now) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(typed, "typed");
        Objects.requireNonNull(now, "now");
        Optional<String> code = backup.get().read(typed);
        if (code.isPresent()) {
            return spendsBackup(account, code.get());
        }
        return appTakes(account, typed, now) || mailed.takes(account, typed.strip());
    }

    /**
     * Whether the code is the app's or a backup code, spending it when it is: the proof that manages the app. A
     * mailed code never turns the app off or renews its backup codes.
     */
    public boolean appOrBackup(UUID account, String typed, Instant now) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(typed, "typed");
        Objects.requireNonNull(now, "now");
        Optional<String> code = backup.get().read(typed);
        return code.isPresent() ? spendsBackup(account, code.get()) : appTakes(account, typed, now);
    }

    /** Hashes a new set for the account in place of every code before it, and gives it back to be shown once. */
    public List<String> newBackupCodes(UUID account, RandomGenerator random) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(random, "random");
        List<String> codes = backup.get().draw(random);
        factors.setBackupCodes(account, codes.stream().map(this::hash).toList());
        return codes;
    }

    private boolean appTakes(UUID account, String typed, Instant now) {
        Optional<Factors.App> app = factors.app(account);
        if (app.isEmpty()) {
            return false;
        }
        OptionalLong step = totp.get()
                .verify(app.get().secret().bytes(), typed, now, app.get().lastStep());
        return step.isPresent() && factors.useStep(account, step.getAsLong());
    }

    private boolean spendsBackup(UUID account, String code) {
        for (PasswordHash stored : factors.backupCodes(account)) {
            if (matches(code, stored)) {
                return factors.useBackupCode(account, stored);
            }
        }
        return false;
    }

    private boolean matches(String code, PasswordHash stored) {
        try (Password typed = Password.of(code)) {
            return !(hashing.verify(typed, stored) instanceof Verification.Wrong);
        }
    }

    private PasswordHash hash(String code) {
        try (Password typed = Password.of(code)) {
            return hashing.hash(typed);
        }
    }
}
