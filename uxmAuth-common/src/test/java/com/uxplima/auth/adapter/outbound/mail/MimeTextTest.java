package com.uxplima.auth.adapter.outbound.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The text of a mail: headers any server reads, the subject and the name in UTF-8, the body in base64. */
final class MimeTextTest {

    private static final ZonedDateTime DATE = ZonedDateTime.of(2026, 10, 18, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final Pattern WORD = Pattern.compile("=\\?UTF-8\\?B\\?([A-Za-z0-9+/=]+)\\?=");

    private static String message(String fromName, String subject, String body) {
        return MimeText.message(
                fromName, "auth@example.com", "steve@example.net", subject, body, DATE, "1234@example.com");
    }

    private static List<String> headers(String message) {
        return Arrays.asList(message.substring(0, message.indexOf("\r\n\r\n")).split("\r\n"));
    }

    private static String header(String message, String name) {
        String unfolded = message.substring(0, message.indexOf("\r\n\r\n")).replace("\r\n ", " ");
        return Arrays.stream(unfolded.split("\r\n"))
                .filter(line -> line.startsWith(name + ": "))
                .map(line -> line.substring(name.length() + 2))
                .findFirst()
                .orElseThrow();
    }

    private static String decoded(String encodedWords) {
        Matcher words = WORD.matcher(encodedWords);
        StringBuilder text = new StringBuilder();
        String rest = encodedWords;
        while (words.find()) {
            text.append(new String(Base64.getDecoder().decode(words.group(1)), StandardCharsets.UTF_8));
            rest = encodedWords.substring(words.end());
        }
        return text + rest;
    }

    private static String body(String message) {
        String encoded = message.substring(message.indexOf("\r\n\r\n") + 4).replace("\r\n", "");
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the headers a server needs, in UTF-8 plain text with a base64 body")
    void theHeadersAreThere() {
        String message = message("Example", "Your code", "Hello");

        assertThat(header(message, "From")).isEqualTo("Example <auth@example.com>");
        assertThat(header(message, "To")).isEqualTo("<steve@example.net>");
        assertThat(header(message, "Subject")).isEqualTo("Your code");
        assertThat(header(message, "Date")).isEqualTo("Sun, 18 Oct 2026 12:00:00 GMT");
        assertThat(header(message, "Message-ID")).isEqualTo("<1234@example.com>");
        assertThat(header(message, "MIME-Version")).isEqualTo("1.0");
        assertThat(header(message, "Content-Type")).isEqualTo("text/plain; charset=UTF-8");
        assertThat(header(message, "Content-Transfer-Encoding")).isEqualTo("base64");
    }

    @Test
    @DisplayName("a subject and a name outside ASCII travel as UTF-8 words and read back the same, however long")
    void utf8WordsReadBack() {
        String subject = "Giriş kodun hazır, şifreni unuttuysan bu kodu kullan: çok güvenli ve tek kullanımlık";
        String message = message("Sunucu Ağı", subject, "x");

        assertThat(decoded(header(message, "Subject"))).isEqualTo(subject);
        assertThat(decoded(header(message, "From"))).isEqualTo("Sunucu Ağı <auth@example.com>");
        assertThat(headers(message))
                .allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(78));
    }

    @Test
    @DisplayName("the body reads back with its lines in CRLF, and no encoded line passes 76 characters")
    void theBodyReadsBack() {
        String body = "Merhaba Steve,\nKodun: 482913\n\n" + "uzun satır ".repeat(40);
        String message = message("Example", "Code", body);

        assertThat(body(message)).isEqualTo(body.replace("\n", "\r\n"));
        String encoded = message.substring(message.indexOf("\r\n\r\n") + 4);
        assertThat(encoded.split("\r\n"))
                .allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(76));
    }

    @Test
    @DisplayName("a line break in a subject or a name cannot start a header of its own")
    void noHeaderInjection() {
        String message = message("Evil\r\nBcc: eve@example.org", "Hi\r\nBcc: eve@example.org", "x");

        assertThat(headers(message)).noneMatch(line -> line.startsWith("Bcc"));
        assertThat(decoded(header(message, "Subject")))
                .as("not even inside the word")
                .isEqualTo("Hi Bcc: eve@example.org");
    }
}
