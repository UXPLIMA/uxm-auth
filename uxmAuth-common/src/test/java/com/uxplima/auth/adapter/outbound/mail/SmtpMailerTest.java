package com.uxplima.auth.adapter.outbound.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.auth.adapter.outbound.MailSettings;
import com.uxplima.auth.application.port.Mailer;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.testing.FakeSmtp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A mail of a kind, in a language, on the mailer's own threads: sent, off, or failed once in the console. */
final class SmtpMailerTest {

    private static final EmailAddress STEVE =
            EmailAddress.parse("steve@example.net").orElseThrow();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path folder;

    private final List<String> console = Collections.synchronizedList(new ArrayList<>());

    private SmtpMailer mailer(FakeSmtp smtp, boolean enabled) throws Exception {
        MailFiles.extractShipped(getClass().getClassLoader(), folder.resolve("mail"));
        MailSettings settings = new MailSettings(
                enabled,
                "localhost",
                smtp.port(),
                MailSettings.Security.PLAIN,
                "",
                "",
                "auth@example.com",
                "Örnek Ağ",
                Duration.ofSeconds(5),
                1);
        return new SmtpMailer(
                () -> settings,
                new MailFiles(folder.resolve("mail")),
                new SmtpClient(smtp.clientTls()),
                () -> Locale.ENGLISH,
                console::add,
                CLOCK,
                2);
    }

    private static String body(String message) {
        String encoded = message.substring(message.indexOf("\r\n\r\n") + 4).replace("\r\n", "");
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a mail is sent on the mailer's own thread, in the player's language, with the values filled")
    void aMailIsSent() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Mode.PLAIN, folder);
                SmtpMailer mailer = mailer(smtp, true)) {
            AtomicReference<String> thread = new AtomicReference<>();

            Mailer.Outcome outcome = mailer.send(
                            Mailer.Kind.LOGIN_CODE,
                            STEVE,
                            Locale.forLanguageTag("tr"),
                            Map.of("player", "Steve", "code", "482913", "minutes", "15", "server", "Örnek"))
                    .whenComplete(
                            (done, failure) -> thread.set(Thread.currentThread().getName()))
                    .join();
            smtp.await();

            assertThat(outcome).isEqualTo(Mailer.Outcome.SENT);
            assertThat(thread.get()).startsWith("uxmauth-mail-");
            assertThat(smtp.lines()).contains("RCPT TO:<steve@example.net>");
            assertThat(body(smtp.messages().getFirst())).contains("Steve", "482913");
            assertThat(console).isEmpty();
        }
    }

    @Test
    @DisplayName("with mail off nothing connects, and the answer says it is off")
    void offSendsNothing() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Mode.PLAIN, folder);
                SmtpMailer mailer = mailer(smtp, false)) {
            assertThat(mailer.send(Mailer.Kind.VERIFY, STEVE, Locale.ENGLISH, Map.of())
                            .join())
                    .isEqualTo(Mailer.Outcome.OFF);
            assertThat(smtp.lines()).isEmpty();
        }
    }

    @Test
    @DisplayName("a mail the server refuses fails, said once in the console without the address")
    void aRefusalIsSaidOnceWithoutTheAddress() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp(FakeSmtp.Mode.PLAIN, folder)
                        .answer("RCPT", "550 5.1.1 <steve@example.net> no such user");
                SmtpMailer mailer = mailer(smtp, true)) {
            assertThat(mailer.send(Mailer.Kind.RECOVERY, STEVE, Locale.ENGLISH, Map.of())
                            .join())
                    .isEqualTo(Mailer.Outcome.FAILED);

            assertThat(console).singleElement().asString().contains("550").contains("RCPT");
            assertThat(String.join("\n", console)).doesNotContain("steve").doesNotContain("example.net");
        }
    }

    @Test
    @DisplayName("a server that cannot be reached fails the mail, said once without the address")
    void anUnreachableServerFails() throws Exception {
        FakeSmtp smtp = new FakeSmtp(FakeSmtp.Mode.PLAIN, folder);
        try (SmtpMailer mailer = mailer(smtp, true)) {
            smtp.close();

            assertThat(mailer.send(Mailer.Kind.VERIFY, STEVE, Locale.ENGLISH, Map.of())
                            .join())
                    .isEqualTo(Mailer.Outcome.FAILED);
            assertThat(console).singleElement().asString().doesNotContain("steve");
        }
    }
}
