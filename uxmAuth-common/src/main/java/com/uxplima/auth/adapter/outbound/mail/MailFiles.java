package com.uxplima.auth.adapter.outbound.mail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.auth.application.port.Mailer;
import com.uxplima.uxmlib.config.ClasspathFiles;
import com.uxplima.uxmlib.config.ConfigException;

/**
 * The mails in the data folder, one file per kind and language: {@code login-code_tr.txt}. A file starts with a
 * {@code Subject:} line, then a blank line, then the body; {@code {code}} and the other placeholders are filled
 * in both. The languages are the files: an operator who writes {@code login-code_de.txt} has German mail.
 */
public final class MailFiles {

    /** Where the mails sit in the jar, and in the data folder. */
    public static final String FOLDER = "mail";

    private static final String SUBJECT = "Subject:";
    private static final String SUFFIX = ".txt";

    private final Path folder;

    /** @param folder the mail folder in the data folder */
    public MailFiles(Path folder) {
        this.folder = Objects.requireNonNull(folder, "folder");
    }

    /** A mail with its placeholders filled. */
    public record Filled(String subject, String body) {
        public Filled {
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(body, "body");
        }
    }

    /**
     * Writes every mail the jar carries into the folder, never over one that is there.
     *
     * @return the files written, which is empty on every start after the first
     */
    public static List<Path> extractShipped(ClassLoader loader, Path folder) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(folder, "folder");
        List<Path> written = new ArrayList<>();
        for (String name : ClasspathFiles.list(loader, FOLDER)) {
            Path file = folder.resolve(name);
            if (!name.endsWith(SUFFIX) || Files.isRegularFile(file)) {
                continue;
            }
            try (InputStream shipped = loader.getResourceAsStream(FOLDER + "/" + name)) {
                if (shipped != null) {
                    Files.createDirectories(folder);
                    Files.copy(shipped, file);
                    written.add(file);
                }
            } catch (IOException unwritable) {
                throw new ConfigException("cannot write the mail file " + file, unwritable);
            }
        }
        return List.copyOf(written);
    }

    /**
     * The mail of this kind in the language, else in the server's own, else in English, with the values in
     * place; nothing when no file of the kind can be read or one has no subject line.
     */
    public Optional<Filled> fill(Mailer.Kind kind, Locale language, Locale fallback, Map<String, String> values) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(values, "values");
        for (String tag : tags(language, fallback)) {
            Path file = folder.resolve(kind.file() + "_" + tag + SUFFIX);
            if (Files.isRegularFile(file)) {
                return read(file).map(mail -> new Filled(filled(mail.subject(), values), filled(mail.body(), values)));
            }
        }
        return Optional.empty();
    }

    /** The tags to try in order: the whole language, its language alone, the server's, English. */
    private static Set<String> tags(Locale language, Locale fallback) {
        Set<String> tags = new LinkedHashSet<>();
        for (Locale locale : List.of(language, fallback, Locale.ENGLISH)) {
            tags.add(locale.toLanguageTag());
            tags.add(locale.getLanguage());
        }
        tags.remove("");
        tags.remove("und");
        return tags;
    }

    private static Optional<Filled> read(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException unreadable) {
            return Optional.empty();
        }
        int end = text.indexOf('\n');
        String first = end < 0 ? text : text.substring(0, end);
        if (!first.startsWith(SUBJECT)) {
            return Optional.empty();
        }
        String body = end < 0 ? "" : text.substring(end + 1);
        return Optional.of(new Filled(first.substring(SUBJECT.length()).strip(), body.strip()));
    }

    private static String filled(String text, Map<String, String> values) {
        String filled = text;
        for (Map.Entry<String, String> value : values.entrySet()) {
            filled = filled.replace("{" + value.getKey() + "}", value.getValue());
        }
        return filled;
    }
}
