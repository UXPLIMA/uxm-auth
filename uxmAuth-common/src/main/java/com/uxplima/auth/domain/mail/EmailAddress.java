package com.uxplima.auth.domain.mail;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An address a mail can go to. It is read strictly: a name, one at sign and a domain of labels joined by dots,
 * with nothing that could end a line of SMTP or close an angle bracket. The domain is kept in lower case; the name
 * is kept as typed, because some servers tell names apart by case.
 */
public final class EmailAddress {

    private static final int LONGEST = 254;
    private static final int LONGEST_NAME = 64;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+");
    private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

    private final String name;
    private final String domain;

    private EmailAddress(String name, String domain) {
        this.name = name;
        this.domain = domain;
    }

    /** The address in this text, or nothing when it is not one. */
    public static Optional<EmailAddress> parse(String text) {
        Objects.requireNonNull(text, "text");
        String typed = text.strip();
        int at = typed.indexOf('@');
        if (typed.length() > LONGEST || at < 1 || at != typed.lastIndexOf('@')) {
            return Optional.empty();
        }
        String name = typed.substring(0, at);
        String domain = typed.substring(at + 1).toLowerCase(Locale.ROOT);
        if (!validName(name) || !validDomain(domain)) {
            return Optional.empty();
        }
        return Optional.of(new EmailAddress(name, domain));
    }

    private static boolean validName(String name) {
        return name.length() <= LONGEST_NAME
                && NAME.matcher(name).matches()
                && !name.startsWith(".")
                && !name.endsWith(".")
                && !name.contains("..");
    }

    private static boolean validDomain(String domain) {
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }

    /** The whole address, for the envelope of a mail and nothing else. */
    public String value() {
        return name + "@" + domain;
    }

    /** The domain, in lower case. */
    public String domain() {
        return domain;
    }

    /** The address as it is shown back: the first letter of the name and of the domain, and the ending. */
    public String masked() {
        String ending = domain.substring(domain.lastIndexOf('.'));
        return name.charAt(0) + "***@" + domain.charAt(0) + "***" + ending;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EmailAddress that && name.equals(that.name) && domain.equals(that.domain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, domain);
    }

    /** Never the address: a log line that prints one by mistake gives nothing away. */
    @Override
    public String toString() {
        return "EmailAddress[***]";
    }
}
