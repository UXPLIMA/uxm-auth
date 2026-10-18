package com.uxplima.auth.adapter.outbound.mail;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The text of a plain mail as RFC 5322 and MIME want it. A subject or a name outside ASCII goes as UTF-8 encoded
 * words, folded so no header line passes 78 characters; the body goes in base64, so no line of it can start with a
 * dot or pass 76 characters, whatever the mail file holds.
 */
final class MimeText {

    private static final String CRLF = "\r\n";
    private static final int LINE = 76;
    /** Bytes of UTF-8 per encoded word, which keeps each word inside 75 characters. */
    private static final int WORD_BYTES = 39;
    /** A line break of any kind, which a header may never carry. */
    private static final Pattern BREAKS = Pattern.compile("[\\r\\n]+");

    private MimeText() {}

    /** The whole message, headers and body, with CRLF line ends. */
    static String message(
            String fromName,
            String fromAddress,
            String to,
            String subject,
            String body,
            ZonedDateTime date,
            String messageId) {
        Objects.requireNonNull(body, "body");
        StringBuilder text = new StringBuilder();
        header(text, "From", phrase(fromName) + " <" + fromAddress + ">");
        header(text, "To", "<" + to + ">");
        header(text, "Subject", phrase(subject));
        header(text, "Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(date.withZoneSameInstant(ZoneOffset.UTC)));
        header(text, "Message-ID", "<" + messageId + ">");
        header(text, "MIME-Version", "1.0");
        header(text, "Content-Type", "text/plain; charset=UTF-8");
        header(text, "Content-Transfer-Encoding", "base64");
        text.append(CRLF);
        String crlf = body.replace("\r\n", "\n").replace("\n", CRLF);
        String encoded = Base64.getEncoder().encodeToString(crlf.getBytes(StandardCharsets.UTF_8));
        for (int start = 0; start < encoded.length(); start += LINE) {
            text.append(encoded, start, Math.min(encoded.length(), start + LINE))
                    .append(CRLF);
        }
        return text.toString();
    }

    private static void header(StringBuilder text, String name, String value) {
        text.append(name).append(": ").append(value).append(CRLF);
    }

    /** The text as it is when it is plain short ASCII, else as encoded words; a line break never survives. */
    private static String phrase(String text) {
        String flat = BREAKS.matcher(text).replaceAll(" ").strip();
        boolean ascii = flat.chars().allMatch(c -> c >= 0x20 && c < 0x7f);
        if (ascii && flat.length() <= LINE - "Subject: ".length()) {
            return flat;
        }
        return String.join(CRLF + " ", words(flat));
    }

    /** UTF-8 encoded words, each whole characters of at most {@link #WORD_BYTES} bytes. */
    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < text.length(); ) {
            int point = text.codePointAt(i);
            String character = new String(Character.toChars(point));
            int size = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > WORD_BYTES) {
                words.add(word(chunk.toString()));
                chunk.setLength(0);
                bytes = 0;
            }
            chunk.append(character);
            bytes += size;
            i += Character.charCount(point);
        }
        words.add(word(chunk.toString()));
        return words;
    }

    private static String word(String text) {
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "?=";
    }
}
