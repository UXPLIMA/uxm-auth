package com.uxplima.auth.domain.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Which domains an address may be at. An empty allowed list takes every domain; a domain names its subdomains
 * too; a denied domain is refused even where the allowed list names it.
 */
public record DomainRule(List<String> allowed, List<String> denied) {

    public DomainRule {
        allowed = clean(Objects.requireNonNull(allowed, "allowed"), "email.allowed-domains");
        denied = clean(Objects.requireNonNull(denied, "denied"), "email.denied-domains");
    }

    /** Whether an address at this domain may be linked. */
    public boolean takes(EmailAddress address) {
        String domain = Objects.requireNonNull(address, "address").domain();
        if (denied.stream().anyMatch(entry -> under(domain, entry))) {
            return false;
        }
        return allowed.isEmpty() || allowed.stream().anyMatch(entry -> under(domain, entry));
    }

    private static boolean under(String domain, String entry) {
        return domain.equals(entry) || domain.endsWith("." + entry);
    }

    private static List<String> clean(List<String> entries, String setting) {
        List<String> clean = new ArrayList<>();
        for (String entry : entries) {
            String domain = entry.strip().toLowerCase(Locale.ROOT);
            if (EmailAddress.parse("x@" + domain).isEmpty()) {
                throw new IllegalArgumentException(setting + " holds " + entry + ", which is not a domain");
            }
            clean.add(domain);
        }
        return List.copyOf(clean);
    }
}
