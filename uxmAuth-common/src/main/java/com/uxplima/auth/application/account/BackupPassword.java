package com.uxplima.auth.application.account;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.password.CharClass;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.password.PasswordVerdict;

/**
 * Gives a premium account with no password a random one, for the day Mojang cannot confirm its player. It is made
 * on the operator's rules, so the login never holds it up to be replaced, stored as a hash like any password, and
 * handed back once for the player to read. An account that has a password gets nothing, so it is never shown twice.
 */
public final class BackupPassword {

    /** Letters and digits a reader cannot mistake for each other. */
    private static final String PLAIN = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final String SYMBOLS = "-_.!#%+=";
    private static final int LENGTH = 12;
    private static final int TRIES = 64;

    private final AccountRepository accounts;
    private final Hashing hashing;
    private final Supplier<PasswordPolicy> policy;
    private final BooleanSupplier on;
    private final RandomGenerator random;

    /** @param random a secure source in production, since the password guards the account */
    public BackupPassword(
            AccountRepository accounts,
            Hashing hashing,
            Supplier<PasswordPolicy> policy,
            BooleanSupplier on,
            RandomGenerator random) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.on = Objects.requireNonNull(on, "on");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * The password this premium account now has, when it had none and the setting is on; empty otherwise. It
     * hashes and writes the table: on the hashing lane.
     */
    public Optional<String> issue(UUID account) {
        Objects.requireNonNull(account, "account");
        Optional<Account> found = accounts.findById(account)
                .filter(premium -> premium.kind() == AccountKind.PREMIUM && premium.password() == null);
        if (!on.getAsBoolean() || found.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> made = made(policy.get());
        if (made.isEmpty()) {
            return Optional.empty();
        }
        try (Password password = Password.of(made.get())) {
            // Written only over no password: one set meanwhile, on this server or another, stands.
            return accounts.replacePassword(account, null, hashing.hash(password)) ? made : Optional.empty();
        }
    }

    /** A random password the rules take, or none when no try of letters, digits and symbols passes them. */
    private Optional<String> made(PasswordPolicy rules) {
        String alphabet = rules.required().contains(CharClass.SYMBOL) ? PLAIN + SYMBOLS : PLAIN;
        int length = Math.min(Math.max(LENGTH, rules.minLength()), rules.maxLength());
        for (int attempt = 0; attempt < TRIES; attempt++) {
            StringBuilder text = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                text.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            try (Password candidate = Password.of(text.toString())) {
                if (rules.check(candidate) instanceof PasswordVerdict.Accepted) {
                    return Optional.of(text.toString());
                }
            }
        }
        return Optional.empty();
    }
}
