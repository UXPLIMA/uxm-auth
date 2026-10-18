package com.uxplima.auth.domain.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Which domains an address may be at: any, only the allowed ones, never the denied ones. */
final class DomainRuleTest {

    private static EmailAddress at(String domain) {
        return EmailAddress.parse("steve@" + domain).orElseThrow();
    }

    @Test
    @DisplayName("with no list every domain is taken")
    void noListTakesAll() {
        assertThat(new DomainRule(List.of(), List.of()).takes(at("anything.example")))
                .isTrue();
    }

    @Test
    @DisplayName("an allowed list takes its domains and their subdomains, in any case, and nothing else")
    void anAllowedListTakesOnlyItsOwn() {
        DomainRule rule = new DomainRule(List.of("Gmail.com", "example.org"), List.of());

        assertThat(rule.takes(at("gmail.com"))).isTrue();
        assertThat(rule.takes(at("mail.example.org"))).isTrue();
        assertThat(rule.takes(at("notgmail.com"))).isFalse();
        assertThat(rule.takes(at("gmail.com.evil.example"))).isFalse();
    }

    @Test
    @DisplayName("a denied domain and its subdomains are refused, even when the allowed list names them too")
    void aDeniedDomainIsRefused() {
        DomainRule rule = new DomainRule(List.of("example.org"), List.of("temp.example.org", "mailinator.com"));

        assertThat(rule.takes(at("temp.example.org"))).isFalse();
        assertThat(rule.takes(at("x.temp.example.org"))).isFalse();
        assertThat(rule.takes(at("example.org"))).isTrue();
        assertThat(new DomainRule(List.of(), List.of("mailinator.com")).takes(at("mailinator.com")))
                .isFalse();
    }

    @Test
    @DisplayName("an entry that is not a domain is refused with the setting's name")
    void aBadEntryIsRefused() {
        assertThatThrownBy(() -> new DomainRule(List.of("not a domain"), List.of()))
                .hasMessageContaining("email.allowed-domains");
        assertThatThrownBy(() -> new DomainRule(List.of(), List.of("@x.com")))
                .hasMessageContaining("email.denied-domains");
    }
}
