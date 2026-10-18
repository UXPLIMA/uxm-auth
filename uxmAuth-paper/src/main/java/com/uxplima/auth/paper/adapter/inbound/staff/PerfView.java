package com.uxplima.auth.paper.adapter.inbound.staff;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.perf.Timings;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The time spent hashing, querying the database and asking Mojang, as staff read it: one line a kind, over the last
 * minute and since start. It reads memory only, so it answers on the sender's own thread at once.
 */
public final class PerfView {

    private final Timings timings;
    private final Messages messages;

    public PerfView(Timings timings, Messages messages) {
        this.timings = Objects.requireNonNull(timings, "timings");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public void show(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        messages.send(sender, Keys.PERF_HEADER);
        for (Timings.Report report : timings.reports()) {
            Timings.Summary minute = report.lastMinute();
            Timings.Summary total = report.sinceStart();
            messages.send(
                    sender,
                    Keys.PERF_LINE,
                    Placeholder.component("kind", messages.render(sender, word(report.kind()))),
                    Text.placeholder("minute-count", String.valueOf(minute.count())),
                    Text.placeholder("minute-average", millis(minute.average())),
                    Text.placeholder("minute-worst", millis(minute.worst())),
                    Text.placeholder("count", String.valueOf(total.count())),
                    Text.placeholder("average", millis(total.average())),
                    Text.placeholder("worst", millis(total.worst())));
        }
    }

    private static Keys word(Timings.Kind kind) {
        return switch (kind) {
            case HASHING -> Keys.PERF_HASHING;
            case DATABASE -> Keys.PERF_DATABASE;
            case MOJANG -> Keys.PERF_MOJANG;
        };
    }

    /** Milliseconds to one decimal, the same in every language. */
    private static String millis(Duration time) {
        return String.format(Locale.ROOT, "%.1f", time.toNanos() / 1_000_000.0);
    }
}
