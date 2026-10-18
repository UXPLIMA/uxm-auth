package com.uxplima.auth.domain.factor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The codes a player keeps for a lost phone. They are drawn from capitals and digits with no pair a reader mixes
 * up (no 0 and O, no 1, I and L), shown in two halves, and read back however the player types them.
 */
public final class BackupCodes {

    /** Every character a code is drawn from. */
    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private static final int FEWEST = 1;
    private static final int MOST = 20;
    private static final int SHORTEST = 8;
    private static final int LONGEST = 16;

    private final int count;
    private final int length;

    /**
     * @param count how many codes a set holds, 1 to 20
     * @param length how many characters a code has, 8 to 16
     */
    public BackupCodes(int count, int length) {
        if (count < FEWEST || count > MOST) {
            throw new IllegalArgumentException("second-factor.backup-codes must be 1 to 20, not " + count);
        }
        if (length < SHORTEST || length > LONGEST) {
            throw new IllegalArgumentException("second-factor.backup-code-length must be 8 to 16, not " + length);
        }
        this.count = count;
        this.length = length;
    }

    /** A new set, no code twice. */
    public List<String> draw(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        Set<String> drawn = new LinkedHashSet<>();
        while (drawn.size() < count) {
            StringBuilder code = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            drawn.add(code.toString());
        }
        return List.copyOf(drawn);
    }

    /** The code as typed, in capitals with no dash or space, when it is shaped like one of ours. */
    public Optional<String> read(String typed) {
        Objects.requireNonNull(typed, "typed");
        String code = typed.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (code.length() != length || !code.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0)) {
            return Optional.empty();
        }
        return Optional.of(code);
    }

    /** The code in two halves joined by a dash, the longer half second. */
    public static String shown(String code) {
        Objects.requireNonNull(code, "code");
        int half = code.length() / 2;
        return code.substring(0, half) + "-" + code.substring(half);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BackupCodes that && count == that.count && length == that.length;
    }

    @Override
    public int hashCode() {
        return Objects.hash(count, length);
    }

    @Override
    public String toString() {
        return "BackupCodes[count=" + count + ", length=" + length + "]";
    }
}
