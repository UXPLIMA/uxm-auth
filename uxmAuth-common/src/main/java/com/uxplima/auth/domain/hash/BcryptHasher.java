package com.uxplima.auth.domain.hash;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.password.Password;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

/**
 * BCrypt, written as {@code $2b$} and read under {@code $2a$}, {@code $2b$} and {@code $2y$}.
 *
 * <p>The three prefixes are the same algorithm for every password an auth plugin could have stored: they
 * differ only in how old implementations mishandled bytes past 255 characters, which no password policy
 * here allows. A hash from AuthMe, LimboAuth, nLogin or a website therefore verifies unchanged.
 */
public final class BcryptHasher implements PasswordHasher {

    private static final Pattern FORMAT = Pattern.compile("^\\$2[aby]\\$(\\d{2})\\$[./A-Za-z0-9]{53}$");
    private static final int SALT_BYTES = 16;

    private final int cost;

    public BcryptHasher(int cost) {
        if (cost < 4 || cost > 31) {
            throw new IllegalArgumentException("a bcrypt cost is between 4 and 31, got " + cost);
        }
        this.cost = cost;
    }

    @Override
    public HashAlgorithm algorithm() {
        return HashAlgorithm.BCRYPT;
    }

    @Override
    public PasswordHash hash(Password password) {
        Objects.requireNonNull(password, "password");
        char[] chars = password.chars();
        try {
            return new PasswordHash(OpenBSDBCrypt.generate("2b", chars, Salts.next(SALT_BYTES), cost));
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    @Override
    public boolean verify(Password password, PasswordHash hash) {
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(hash, "hash");
        if (!FORMAT.matcher(hash.encoded()).matches()) {
            return false;
        }
        char[] chars = password.chars();
        try {
            return OpenBSDBCrypt.checkPassword(hash.encoded(), chars);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    @Override
    public boolean isCurrent(PasswordHash hash) {
        Objects.requireNonNull(hash, "hash");
        Matcher parts = FORMAT.matcher(hash.encoded());
        return parts.matches() && Integer.parseInt(parts.group(1)) == cost;
    }
}
