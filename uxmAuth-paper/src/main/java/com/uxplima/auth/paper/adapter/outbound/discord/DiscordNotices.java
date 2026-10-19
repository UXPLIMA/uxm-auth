package com.uxplima.auth.paper.adapter.outbound.discord;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.uxplima.auth.adapter.outbound.DiscordSettings;
import com.uxplima.auth.application.port.AuthNotices;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.protection.AddressMask;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.uxmlib.discord.DiscordWebhook;

/**
 * Tells a Discord channel what happened, in the operator's words. Each message waits in a bounded queue for the one
 * thread of its own, so a caller on a server thread returns at once; when too many wait, the oldest is dropped with
 * a line in the console. An address leaves masked. A message Discord refuses, after the one retry the webhook makes
 * on a rate limit, is dropped with one line.
 */
public final class DiscordNotices implements AuthNotices, AutoCloseable {

    /** How one message is posted: the webhook's answer is its HTTP status. */
    @FunctionalInterface
    public interface Post {
        CompletableFuture<Integer> post(String url, String content);
    }

    /** Posts through uxmLib's webhook, which never pings anybody and retries a rate limit once. */
    public static final Post WEBHOOK = (url, content) -> new DiscordWebhook(url).sendContent(content);

    /** Discord's longest message. */
    private static final int MOST = 2000;

    /** Longer than the webhook's own timeout and its one retry, so a stuck post ends here at the latest. */
    private static final long WAIT_SECONDS = 45;

    private final Supplier<DiscordSettings> settings;
    private final Post post;
    private final Consumer<String> console;
    private final ThreadPoolExecutor worker;

    /** @param queue how many messages may wait */
    public DiscordNotices(Supplier<DiscordSettings> settings, Post post, Consumer<String> console, int queue) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.post = Objects.requireNonNull(post, "post");
        this.console = Objects.requireNonNull(console, "console");
        this.worker = new ThreadPoolExecutor(
                1,
                1,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queue),
                Thread.ofPlatform().name("uxmauth-discord-", 1).daemon(true).factory(),
                (task, pool) -> {
                    if (!pool.isShutdown() && pool.getQueue().poll() != null) {
                        console.accept("A Discord message was dropped: too many waited already.");
                        pool.execute(task);
                    }
                });
        worker.allowCoreThreadTimeOut(true);
    }

    @Override
    public void registered(PlayerName name, String address) {
        tell(DiscordSettings.Event.REGISTER, Map.of("player", name.value(), "address", AddressMask.of(address)));
    }

    @Override
    public void loggedIn(PlayerName name, String address, AuthMethod method) {
        tell(
                DiscordSettings.Event.LOGIN,
                Map.of(
                        "player",
                        name.value(),
                        "address",
                        AddressMask.of(address),
                        "method",
                        method.name().toLowerCase(Locale.ROOT)));
    }

    @Override
    public void knownAddress(PlayerName name, String address, int accounts) {
        tell(
                DiscordSettings.Event.KNOWN_ADDRESS,
                Map.of(
                        "player",
                        name.value(),
                        "address",
                        AddressMask.of(address),
                        "accounts",
                        String.valueOf(accounts)));
    }

    @Override
    public void banned(String address, Duration length) {
        tell(DiscordSettings.Event.BAN, Map.of("address", AddressMask.of(address), "time", time(length)));
    }

    @Override
    public void conflictSettled(String name) {
        tell(DiscordSettings.Event.CONFLICT, Map.of("player", name));
    }

    private void tell(DiscordSettings.Event event, Map<String, String> values) {
        DiscordSettings now = settings.get();
        DiscordSettings.Notice notice = now.notice(event);
        if (!now.on() || !notice.enabled()) {
            return;
        }
        String text = fill(notice.message(), values, now.server());
        String url = now.url();
        worker.execute(() -> deliver(url, text));
    }

    /** The operator's words with every value in, and nothing in a value this event does not have. */
    private static String fill(String message, Map<String, String> values, String server) {
        String text = message.replace("{server}", server);
        for (String key : new String[] {"player", "address", "method", "accounts", "time"}) {
            text = text.replace("{" + key + "}", values.getOrDefault(key, ""));
        }
        return text.length() > MOST ? text.substring(0, MOST) : text;
    }

    private void deliver(String url, String text) {
        try {
            int status = post.post(url, text).get(WAIT_SECONDS, TimeUnit.SECONDS);
            if (status / 100 != 2) {
                console.accept("A Discord message was dropped: Discord answered " + status + ".");
            }
        } catch (ExecutionException failed) {
            // The cause's kind says what went wrong; its message can hold the webhook's address, which is a secret.
            Throwable cause = failed.getCause() == null ? failed : failed.getCause();
            console.accept("A Discord message was dropped: " + cause.getClass().getSimpleName() + ".");
        } catch (TimeoutException | RuntimeException failed) {
            console.accept("A Discord message was dropped: " + failed.getClass().getSimpleName() + ".");
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    /** A length as Discord reads it in any language: 1h 30m, 5m, 45s. */
    static String time(Duration length) {
        long seconds = Math.max(0, length.toSeconds());
        StringBuilder out = new StringBuilder();
        if (seconds >= 3600) {
            out.append(seconds / 3600).append("h ");
        }
        if (seconds % 3600 >= 60) {
            out.append(seconds % 3600 / 60).append("m ");
        }
        if (seconds % 60 > 0 || out.isEmpty()) {
            out.append(seconds % 60).append("s ");
        }
        return out.toString().trim();
    }

    /** Lets the messages still waiting go. */
    @Override
    public void close() {
        worker.shutdownNow();
    }
}
