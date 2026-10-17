package com.uxplima.auth.domain.name;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The operator's rules for a name: a pattern, a length, and words nobody may use.
 *
 * <p>The length is checked before the pattern, so a name that is both too long and oddly spelled is told the
 * thing it can fix first.
 */
public record NamePolicy(Pattern pattern, int minLength, int maxLength, List<String> blockedWords) {

    public NamePolicy {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(blockedWords, "blockedWords");
        if (minLength < 1 || maxLength < minLength) {
            throw new IllegalArgumentException(
                    "the length must be at least 1 and the maximum at least the minimum, got " + minLength + " and "
                            + maxLength);
        }
        blockedWords =
                blockedWords.stream().map(word -> word.toLowerCase(Locale.ROOT)).toList();
    }

    /** What the policy says about one name. */
    public NameVerdict check(String raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.length() < minLength) {
            return new NameVerdict.TooShort(minLength);
        }
        if (raw.length() > maxLength) {
            return new NameVerdict.TooLong(maxLength);
        }
        if (!pattern.matcher(raw).matches()) {
            return new NameVerdict.BadCharacters();
        }
        return blockedIn(raw)
                .<NameVerdict>map(NameVerdict.Blocked::new)
                .orElseGet(() -> new NameVerdict.Accepted(new PlayerName(raw)));
    }

    /** The first blocked word the name holds, in any case. */
    public Optional<String> blockedIn(String raw) {
        Objects.requireNonNull(raw, "raw");
        String folded = raw.toLowerCase(Locale.ROOT);
        return blockedWords.stream().filter(folded::contains).findFirst();
    }
}
