package com.uxplima.auth.adapter.outbound.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import com.uxplima.auth.application.port.Mailer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The mails on disk: one file per kind and language, written once, read in the player's language. */
final class MailFilesTest {

    @TempDir
    Path folder;

    @Test
    @DisplayName(
            "every shipped mail is written once, for every kind in English and Turkish, and an edit is never overwritten")
    void shippedMailsAreWrittenOnce() throws Exception {
        assertThat(MailFiles.extractShipped(getClass().getClassLoader(), folder))
                .isNotEmpty();
        for (Mailer.Kind kind : Mailer.Kind.values()) {
            for (String tag : new String[] {"en", "tr"}) {
                assertThat(folder.resolve(kind.file() + "_" + tag + ".txt")).isRegularFile();
            }
        }
        Path edited = folder.resolve(Mailer.Kind.RECOVERY.file() + "_en.txt");
        Files.writeString(edited, "Subject: mine\n\nmine", StandardCharsets.UTF_8);

        assertThat(MailFiles.extractShipped(getClass().getClassLoader(), folder))
                .isEmpty();
        assertThat(Files.readString(edited)).isEqualTo("Subject: mine\n\nmine");
    }

    @Test
    @DisplayName("a mail is read in the player's language, with every placeholder filled in the subject and the body")
    void aMailIsReadInTheLanguage() throws Exception {
        MailFiles.extractShipped(getClass().getClassLoader(), folder);

        MailFiles.Filled mail = new MailFiles(folder)
                .fill(
                        Mailer.Kind.LOGIN_CODE,
                        Locale.forLanguageTag("tr"),
                        Locale.ENGLISH,
                        Map.of("player", "Steve", "code", "482913", "minutes", "15", "server", "Örnek"))
                .orElseThrow();

        assertThat(mail.subject()).doesNotContain("{").contains("Örnek");
        assertThat(mail.body()).contains("Steve", "482913", "15").doesNotContain("{code}", "{player}");
        assertThat(mail.body()).containsAnyOf("kod", "Kod");
    }

    @Test
    @DisplayName(
            "a language with no file reads the server's default, then English; a file an operator adds is a language")
    void aMissingLanguageFallsBack() throws Exception {
        MailFiles.extractShipped(getClass().getClassLoader(), folder);
        MailFiles files = new MailFiles(folder);

        assertThat(files.fill(Mailer.Kind.VERIFY, Locale.GERMAN, Locale.forLanguageTag("tr"), Map.of()))
                .hasValueSatisfying(mail -> assertThat(mail.subject())
                        .isEqualTo(files.fill(Mailer.Kind.VERIFY, Locale.forLanguageTag("tr"), Locale.ENGLISH, Map.of())
                                .orElseThrow()
                                .subject()));
        Files.writeString(
                folder.resolve(Mailer.Kind.VERIFY.file() + "_de.txt"),
                "Subject: Dein Code {code}\n\nHallo {player}",
                StandardCharsets.UTF_8);
        assertThat(files.fill(Mailer.Kind.VERIFY, Locale.GERMAN, Locale.ENGLISH, Map.of("code", "1", "player", "S")))
                .contains(new MailFiles.Filled("Dein Code 1", "Hallo S"));
        Files.delete(folder.resolve(Mailer.Kind.VERIFY.file() + "_de.txt"));
        Files.delete(folder.resolve(Mailer.Kind.VERIFY.file() + "_tr.txt"));
        assertThat(files.fill(Mailer.Kind.VERIFY, Locale.GERMAN, Locale.forLanguageTag("tr"), Map.of()))
                .hasValueSatisfying(mail -> assertThat(mail.subject()).isNotBlank());
    }

    @Test
    @DisplayName("a file with no subject line, or none at all in any language, sends nothing")
    void noSubjectNoMail() throws Exception {
        Files.writeString(folder.resolve(Mailer.Kind.VERIFY.file() + "_en.txt"), "just a body", StandardCharsets.UTF_8);
        MailFiles files = new MailFiles(folder);

        assertThat(files.fill(Mailer.Kind.VERIFY, Locale.ENGLISH, Locale.ENGLISH, Map.of()))
                .isEmpty();
        assertThat(files.fill(Mailer.Kind.RECOVERY, Locale.ENGLISH, Locale.ENGLISH, Map.of()))
                .isEmpty();
    }
}
