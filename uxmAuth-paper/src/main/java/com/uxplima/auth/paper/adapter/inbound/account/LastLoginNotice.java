package com.uxplima.auth.paper.adapter.inbound.account;

import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.protection.AddressMask;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.paper.adapter.inbound.hold.LoginRecords;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * After a login the player reads when and from where the previous one was, the address masked, so a login that
 * was not theirs stands out. It hears of the login before the login is written, so the newest one in the history
 * is the previous one. A registration has none to tell.
 */
public final class LastLoginNotice implements LoginRecords.Landed {

    private final LoginHistory history;
    private final BooleanSupplier on;
    private final Function<PlayerName, @Nullable Player> online;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Supplier<DateTimeFormatter> dates;

    /** @param online the player under this name on this server, when there is one */
    public LastLoginNotice(
            LoginHistory history,
            BooleanSupplier on,
            Function<PlayerName, @Nullable Player> online,
            Scheduler scheduler,
            Messages messages,
            Supplier<DateTimeFormatter> dates) {
        this.history = Objects.requireNonNull(history, "history");
        this.on = Objects.requireNonNull(on, "on");
        this.online = Objects.requireNonNull(online, "online");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.dates = Objects.requireNonNull(dates, "dates");
    }

    /** Reads the history, so it runs off the server's threads, and tells the player on their own. */
    @Override
    public void landed(UUID account, PlayerName name, String address, AuthMethod method) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(name, "name");
        if (!on.getAsBoolean() || Objects.requireNonNull(method, "method") == AuthMethod.REGISTERED) {
            return;
        }
        history.recent(account, 1).stream().findFirst().ifPresent(previous -> {
            Player player = online.apply(name);
            if (player != null) {
                String date = dates.get().format(previous.at());
                String masked = AddressMask.of(previous.address());
                scheduler.entity(
                        player,
                        () -> messages.send(
                                player,
                                Keys.ACCOUNT_LAST_LOGIN,
                                Text.placeholder("date", date),
                                Text.placeholder("address", masked)));
            }
        });
    }
}
