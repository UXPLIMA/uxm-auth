package com.uxplima.auth.domain.premium;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The addresses an operator lists, each a host name in which a star stands for one label: {@code *.example.org}
 * matches {@code eu.example.org} and not {@code a.b.example.org}.
 *
 * <p>The host a client sends may carry a port, a trailing dot, or what a modded client appends after a zero
 * character; each is cut before the match.
 */
public final class HostPatterns {

    private final List<Pattern> patterns;

    public HostPatterns(List<String> written) {
        this.patterns = Objects.requireNonNull(written, "written").stream()
                .map(HostPatterns::compile)
                .toList();
    }

    /** Whether the host a client joined through is one of these. */
    public boolean matches(String host) {
        String plain = plain(Objects.requireNonNull(host, "host"));
        return patterns.stream().anyMatch(pattern -> pattern.matcher(plain).matches());
    }

    private static Pattern compile(String written) {
        String[] labels = plain(written).split("\\.", -1);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < labels.length; i++) {
            if (i > 0) {
                regex.append("\\.");
            }
            regex.append(labels[i].equals("*") ? "[^.]+" : Pattern.quote(labels[i]));
        }
        return Pattern.compile(regex.toString());
    }

    private static String plain(String host) {
        String plain = host;
        int zero = plain.indexOf('\0');
        if (zero >= 0) {
            plain = plain.substring(0, zero);
        }
        int colon = plain.lastIndexOf(':');
        if (colon >= 0 && plain.indexOf(':') == colon) {
            plain = plain.substring(0, colon);
        }
        if (plain.endsWith(".")) {
            plain = plain.substring(0, plain.length() - 1);
        }
        return plain.trim().toLowerCase(Locale.ROOT);
    }
}
