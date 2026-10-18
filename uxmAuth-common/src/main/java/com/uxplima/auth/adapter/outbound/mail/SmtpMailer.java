package com.uxplima.auth.adapter.outbound.mail;

import java.io.IOException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.uxplima.auth.adapter.outbound.MailSettings;
import com.uxplima.auth.application.port.Mailer;
import com.uxplima.auth.domain.mail.EmailAddress;

/**
 * Mail out through the operator's SMTP server, on threads of its own: a mail waits on a slow server, and nothing
 * else waits with it. A mail that fails says why once in the console, never to whom it was going.
 */
public final class SmtpMailer implements Mailer, AutoCloseable {

    private static final int QUEUE = 100;

    private final Supplier<MailSettings> settings;
    private final MailFiles files;
    private final SmtpClient client;
    private final Supplier<Locale> fallback;
    private final Consumer<String> console;
    private final Clock clock;
    private final ThreadPoolExecutor pool;

    /**
     * @param fallback the server's own language, for a player whose language has no mail file
     * @param threads how many mails go out at once
     */
    public SmtpMailer(
            Supplier<MailSettings> settings,
            MailFiles files,
            SmtpClient client,
            Supplier<Locale> fallback,
            Consumer<String> console,
            Clock clock,
            int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("a mailer needs at least one thread, not " + threads);
        }
        this.settings = Objects.requireNonNull(settings, "settings");
        this.files = Objects.requireNonNull(files, "files");
        this.client = Objects.requireNonNull(client, "client");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.console = Objects.requireNonNull(console, "console");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pool = new ThreadPoolExecutor(
                threads,
                threads,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE),
                Thread.ofPlatform().name("uxmauth-mail-", 1).daemon(true).factory());
        pool.allowCoreThreadTimeOut(true);
    }

    @Override
    public CompletableFuture<Outcome> send(Kind kind, EmailAddress to, Locale language, Map<String, String> values) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(language, "language");
        Map<String, String> filled = Map.copyOf(Objects.requireNonNull(values, "values"));
        MailSettings now = settings.get();
        if (!now.enabled()) {
            return CompletableFuture.completedFuture(Outcome.OFF);
        }
        try {
            return CompletableFuture.supplyAsync(() -> deliver(now, kind, to, language, filled), pool);
        } catch (RejectedExecutionException full) {
            console.accept("A " + kind.file() + " mail was not sent: too many mails wait already.");
            return CompletableFuture.completedFuture(Outcome.FAILED);
        }
    }

    private Outcome deliver(MailSettings now, Kind kind, EmailAddress to, Locale language, Map<String, String> values) {
        Optional<MailFiles.Filled> mail = files.fill(kind, language, fallback.get(), values);
        if (mail.isEmpty()) {
            console.accept("A " + kind.file() + " mail was not sent: no mail file of that kind could be read.");
            return Outcome.FAILED;
        }
        String message = MimeText.message(
                now.fromName(),
                now.fromAddress(),
                to.value(),
                mail.get().subject(),
                mail.get().body(),
                ZonedDateTime.now(clock),
                UUID.randomUUID() + "@"
                        + EmailAddress.parse(now.fromAddress())
                                .map(EmailAddress::domain)
                                .orElse("uxmauth"));
        try {
            client.send(now, to.value(), message);
            return Outcome.SENT;
        } catch (IOException | RuntimeException failed) {
            console.accept("A " + kind.file() + " mail was not sent: " + reason(failed));
            return Outcome.FAILED;
        }
    }

    /** Why, in words that never quote the address: a refusal names its step, anything else its kind. */
    private static String reason(Exception failed) {
        if (failed instanceof SmtpRefused refused) {
            return refused.getMessage() + ".";
        }
        return failed.getClass().getSimpleName() + " talking to the mail server.";
    }

    @Override
    public void close() {
        pool.shutdown();
    }
}
