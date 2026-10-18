package com.uxplima.auth.adapter.outbound.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

import com.uxplima.auth.adapter.outbound.MailSettings;
import com.uxplima.auth.testing.FakeSmtp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** One mail over SMTP: in the clear, over TLS from the start, or upgraded by STARTTLS, with or without a login. */
final class SmtpClientTest {

    private static final String MESSAGE = "Subject: hi\r\n\r\n.starts with a dot\r\nbody\r\n";

    @TempDir
    Path folder;

    private static MailSettings settings(int port, MailSettings.Security security, String user) {
        return new MailSettings(
                true,
                "localhost",
                port,
                security,
                user,
                user.isEmpty() ? "" : "s3cret pass",
                "auth@example.com",
                "Example",
                Duration.ofSeconds(5),
                1);
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("in the clear with no login: greeting, EHLO, the envelope, the message dot-stuffed, and QUIT")
    void plainWithNoLogin() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Mode.PLAIN, folder)) {
            new SmtpClient(smtp.clientTls())
                    .send(settings(smtp.port(), MailSettings.Security.PLAIN, ""), "steve@example.net", MESSAGE);
            smtp.await();

            assertThat(smtp.lines())
                    .containsExactly(
                            "EHLO example.com",
                            "MAIL FROM:<auth@example.com>",
                            "RCPT TO:<steve@example.net>",
                            "DATA",
                            "QUIT");
            assertThat(smtp.messages()).containsExactly(MESSAGE);
        }
    }

    @Test
    @DisplayName("TLS from the first byte, with AUTH PLAIN when the server offers it")
    void tlsWithPlainLogin() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Mode.TLS, folder)) {
            new SmtpClient(smtp.clientTls())
                    .send(settings(smtp.port(), MailSettings.Security.SSL, "mailer"), "steve@example.net", MESSAGE);
            smtp.await();

            assertThat(smtp.lines()).contains("AUTH PLAIN " + b64("\0mailer\0s3cret pass"));
            assertThat(smtp.messages()).containsExactly(MESSAGE);
        }
    }

    @Test
    @DisplayName(
            "STARTTLS upgrades before the login, says EHLO again, and AUTH LOGIN serves a server that offers only it")
    void startTlsWithLoginLogin() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Mode.STARTTLS, folder).auth("LOGIN")) {
            new SmtpClient(smtp.clientTls())
                    .send(
                            settings(smtp.port(), MailSettings.Security.STARTTLS, "mailer"),
                            "steve@example.net",
                            MESSAGE);
            smtp.await();

            assertThat(smtp.lines())
                    .startsWith(
                            "EHLO example.com",
                            "STARTTLS",
                            "EHLO example.com",
                            "AUTH LOGIN",
                            b64("mailer"),
                            b64("s3cret pass"));
            assertThat(smtp.messages()).containsExactly(MESSAGE);
        }
    }

    @Test
    @DisplayName("a certificate for another name is refused, so a server in the middle cannot read the login")
    void aCertificateForAnotherNameIsRefused() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Mode.TLS, folder, "mail.other.example")) {
            SmtpClient client = new SmtpClient(smtp.clientTls());

            assertThatThrownBy(() -> client.send(
                            settings(smtp.port(), MailSettings.Security.SSL, "mailer"), "steve@example.net", MESSAGE))
                    .isInstanceOf(javax.net.ssl.SSLHandshakeException.class);
            assertThat(smtp.lines()).isEmpty();
        }
    }

    @Test
    @DisplayName("a server that offers no STARTTLS is never sent the login or the mail in the clear")
    void noStartTlsNoMail() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Mode.STARTTLS, folder).offersStartTls(false)) {
            SmtpClient client = new SmtpClient(smtp.clientTls());

            assertThatThrownBy(() -> client.send(
                            settings(smtp.port(), MailSettings.Security.STARTTLS, "mailer"),
                            "steve@example.net",
                            MESSAGE))
                    .isInstanceOf(SmtpRefused.class)
                    .hasMessageContaining("STARTTLS");
            smtp.await();
            assertThat(smtp.lines()).noneMatch(line -> line.startsWith("AUTH") || line.startsWith("MAIL"));
        }
    }

    @Test
    @DisplayName("a login the server does not speak is refused before anything is sent")
    void noMechanismNoLogin() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Mode.PLAIN, folder).auth("CRAM-MD5")) {
            SmtpClient client = new SmtpClient(smtp.clientTls());

            assertThatThrownBy(() -> client.send(
                            settings(smtp.port(), MailSettings.Security.PLAIN, "mailer"), "steve@example.net", MESSAGE))
                    .isInstanceOf(SmtpRefused.class)
                    .hasMessageContaining("AUTH");
        }
    }

    @Test
    @DisplayName("a refusal names the step and the code, never the address or what the server said about it")
    void aRefusalNamesTheStep() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Mode.PLAIN, folder)
                .answer("RCPT", "550 5.1.1 <steve@example.net> no such user")) {
            SmtpClient client = new SmtpClient(smtp.clientTls());

            assertThatThrownBy(() -> client.send(
                            settings(smtp.port(), MailSettings.Security.PLAIN, ""), "steve@example.net", MESSAGE))
                    .isInstanceOfSatisfying(SmtpRefused.class, refused -> {
                        assertThat(refused.code()).isEqualTo(550);
                        assertThat(refused.step()).isEqualTo("RCPT");
                    })
                    .message()
                    .doesNotContain("steve")
                    .doesNotContain("example.net");
        }
    }

    @Test
    @DisplayName("an address that could smuggle a command is refused before the connection")
    void anAddressWithALineBreakIsRefused() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Mode.PLAIN, folder)) {
            SmtpClient client = new SmtpClient(smtp.clientTls());

            assertThatThrownBy(() -> client.send(
                            settings(smtp.port(), MailSettings.Security.PLAIN, ""),
                            "steve@example.net>\r\nRCPT TO:<eve@example.org",
                            MESSAGE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(smtp.lines()).isEmpty();
        }
    }
}
