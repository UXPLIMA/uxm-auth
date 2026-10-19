package com.uxplima.auth.paper.adapter.inbound.account;

import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.protection.AddressMask;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.paper.adapter.inbound.hold.LoginRecords;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * After a login the player reads when and from where the previous one was, the address masked, so a login that
 * was not theirs stands out. It hears of the login before the login is written, so the newest one in the history
 * is the previous one. A registration has none to tell.
 */
public final class LastLoginNotice implements LoginRecords.Landed {

    private final LoginHistory history;
    private final BooleanSupplier on;
    private final LoginNotices.Reach reach;
    private final Messages messages;
    private final Supplier<DateTimeFormatter> dates;

    public LastLoginNotice(
            LoginHistory history,
            BooleanSupplier on,
            LoginNotices.Reach reach,
            Messages messages,
            Supplier<DateTimeFormatter> dates) {
        this.history = Objects.requireNonNull(history, "history");
        this.on = Objects.requireNonNull(on, "on");
        this.reach = Objects.requireNonNull(reach, "reach");
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
            String date = dates.get().format(previous.at());
            String masked = AddressMask.of(previous.address());
            reach.tell(
                    name,
                    account,
                    player -> messages.send(
                            player,
                            Keys.ACCOUNT_LAST_LOGIN,
                            Text.placeholder("date", date),
                            Text.placeholder("address", masked)));
        });
    }
}
