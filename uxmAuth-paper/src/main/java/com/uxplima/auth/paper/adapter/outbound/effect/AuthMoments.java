package com.uxplima.auth.paper.adapter.outbound.effect;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;
import com.uxplima.uxmlib.condition.ItemStore;
import com.uxplima.uxmlib.condition.Wallet;
import com.uxplima.uxmlib.condition.action.ActionContext;
import com.uxplima.uxmlib.condition.action.ActionCostException;
import com.uxplima.uxmlib.condition.action.ActionList;
import com.uxplima.uxmlib.condition.wallet.BridgedWallet;
import com.uxplima.uxmlib.condition.wallet.Economies;
import com.uxplima.uxmlib.content.Operands;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.message.MessageKey;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import org.jspecify.annotations.Nullable;

/**
 * Runs the list an operator wrote for one moment, for the player it happened to.
 *
 * <p>Every line is run on its own, so a line the engine cannot read costs that line and not the moment. A line
 * that names a key is read in the language of whoever reads it, and a {@code [broadcast]} of a key is rendered
 * once per reader, so nobody reads another player's language.
 */
public final class AuthMoments implements MomentRunner {

    /** What marks a payload as a line of the catalogue rather than the operator's own words. */
    private static final String KEY_MARK = "@";

    /** The one verb that reaches everybody rather than the player the moment happened to. */
    private static final String BROADCAST = "[broadcast]";

    private final Server server;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Styler styler;
    private final Supplier<AuthSettings.Effects> effects;
    private final Wallet wallet;
    private final Function<UUID, Optional<Duration>> timeLeft;
    private final BiConsumer<Player, BossBar> barShown;

    public AuthMoments(
            Server server,
            Scheduler scheduler,
            Messages messages,
            Styler styler,
            Supplier<AuthSettings.Effects> effects) {
        this(server, scheduler, messages, styler, effects, id -> Optional.empty());
    }

    /** @param timeLeft how long a waiting player has left, which {@code %uxmauth_time_left%} reads in seconds */
    public AuthMoments(
            Server server,
            Scheduler scheduler,
            Messages messages,
            Styler styler,
            Supplier<AuthSettings.Effects> effects,
            Function<UUID, Optional<Duration>> timeLeft) {
        this(server, scheduler, messages, styler, effects, timeLeft, (player, bar) -> {});
    }

    /** @param barShown who keeps a bar a line puts up, so the wait that showed it can take it down */
    public AuthMoments(
            Server server,
            Scheduler scheduler,
            Messages messages,
            Styler styler,
            Supplier<AuthSettings.Effects> effects,
            Function<UUID, Optional<Duration>> timeLeft,
            BiConsumer<Player, BossBar> barShown) {
        this.barShown = Objects.requireNonNull(barShown, "barShown");
        this.timeLeft = Objects.requireNonNull(timeLeft, "timeLeft");
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.styler = Objects.requireNonNull(styler, "styler");
        this.effects = Objects.requireNonNull(effects, "effects");
        // Whatever economy the server already has, read through the library's bridge. Left unwired the
        // library reads every balance as zero and a [take-money] line is skipped with nothing saying why.
        this.wallet = BridgedWallet.ofServer(Economies.vault(), System.getLogger("uxmAuth"));
    }

    @Override
    public void fire(Player player, AuthSettings.Moment moment) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(moment, "moment");
        List<String> lines = effects.get().of(moment);
        if (lines.isEmpty()) {
            return;
        }
        ActionContext forThePlayer = contextFor(player, server, player);
        for (String line : lines) {
            if (namesAKey(line) && reachesEverybody(line)) {
                for (Player viewer : server.getOnlinePlayers()) {
                    runOne(line, contextFor(player, viewer, viewer));
                }
                continue;
            }
            runOne(line, forThePlayer);
        }
    }

    private static boolean namesAKey(String line) {
        int close = line.indexOf(']');
        return close >= 0 && line.substring(close + 1).strip().startsWith(KEY_MARK);
    }

    private static boolean reachesEverybody(String line) {
        int close = line.indexOf(']');
        return close >= 0 && line.substring(0, close + 1).strip().equals(BROADCAST);
    }

    private void runOne(String line, ActionContext context) {
        try {
            ActionList.parse(List.of(line)).run(context);
        } catch (IllegalArgumentException unreadable) {
            server.getLogger()
                    .warning("This line of an effects list cannot be read, so it did nothing: " + line + " ("
                            + unreadable.getMessage() + ")");
        } catch (ActionCostException unaffordable) {
            // A cost nobody can pay is the operator's own gate doing its job, so it is not a warning.
            server.getLogger()
                    .fine("This line of an effects list asked for a cost the player could not pay: " + line + " ("
                            + unaffordable.getMessage() + ")");
        }
    }

    /**
     * The player's name and id first, which every line may need and which must not wait on PlaceholderAPI and
     * its Player expansion being installed, then everything else the server's placeholders answer.
     */
    private String resolve(@Nullable Player player, String template) {
        String filled = player == null
                ? template
                : template.replace("%player_name%", player.getName())
                        .replace("%player_uuid%", player.getUniqueId().toString())
                        .replace("%uxmauth_time_left%", secondsLeft(player.getUniqueId()));
        return Operands.standard().resolve(player, filled);
    }

    /** The seconds a waiting player has left, rounded up so the last second reads 1; 0 for one who does not wait. */
    private String secondsLeft(UUID id) {
        long millis = timeLeft.apply(id).map(Duration::toMillis).orElse(0L);
        return String.valueOf((millis + 999) / 1000);
    }

    /** A context for one moment: who it happened to, who hears a broadcast, and whose language is read. */
    private ActionContext contextFor(Player player, Audience room, Player reader) {
        return ActionContext.builder(this::resolve)
                .player(player)
                .style(line -> styler.tokens(line, messages.localeOf(reader)))
                .words(path -> MiniMessage.miniMessage().serialize(messages.render(reader, MessageKey.of(path, path))))
                .itemStore(ItemStore.inventory())
                .wallet(wallet)
                .broadcast(room)
                .consoleSink(line -> server.dispatchCommand(server.getConsoleSender(), line))
                .playerSink(line -> server.dispatchCommand(player, line))
                // [bossbar] puts a bar up and asks the caller to take it down again later.
                .later((delay, what) -> scheduler.entityLater(player, delay, what))
                .barShown(barShown)
                .build();
    }
}
