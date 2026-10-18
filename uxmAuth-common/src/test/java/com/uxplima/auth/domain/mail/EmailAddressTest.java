package com.uxplima.auth.domain.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** An address a mail can go to: one name, one domain with a dot, nothing that could break a line of SMTP. */
final class EmailAddressTest {

    @Test
    @DisplayName("an address is read with its domain in lower case and its name as typed, without spaces around")
    void anAddressIsRead() {
        assertThat(EmailAddress.parse("  Steve.Smith+mc@Example.NET ")).hasValueSatisfying(address -> {
            assertThat(address.value()).isEqualTo("Steve.Smith+mc@example.net");
            assertThat(address.domain()).isEqualTo("example.net");
        });
    }

    @Test
    @DisplayName(
            "no name, no domain, no dot in the domain, two at signs, a space, a line break or angle brackets: no address")
    void brokenAddressesAreRefused() {
        assertThat(EmailAddress.parse("@example.net")).isEmpty();
        assertThat(EmailAddress.parse("steve@")).isEmpty();
        assertThat(EmailAddress.parse("steve@localhost")).isEmpty();
        assertThat(EmailAddress.parse("steve@@example.net")).isEmpty();
        assertThat(EmailAddress.parse("st eve@example.net")).isEmpty();
        assertThat(EmailAddress.parse("steve@example.net\r\nRCPT TO:<eve@example.org>"))
                .isEmpty();
        assertThat(EmailAddress.parse("<steve@example.net>")).isEmpty();
        assertThat(EmailAddress.parse("steve@example..net")).isEmpty();
        assertThat(EmailAddress.parse("steve@-example.net")).isEmpty();
        assertThat(EmailAddress.parse("x".repeat(65) + "@example.net")).isEmpty();
        assertThat(EmailAddress.parse("steve@" + "a".repeat(250) + ".net")).isEmpty();
    }

    @Test
    @DisplayName("an address never prints itself whole, so a log line that names one shows only its mask")
    void itNeverPrintsItself() {
        assertThat(EmailAddress.parse("steve@example.net").orElseThrow().toString())
                .doesNotContain("steve")
                .doesNotContain("example");
    }
}
