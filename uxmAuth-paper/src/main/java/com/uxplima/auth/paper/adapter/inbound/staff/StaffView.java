package com.uxplima.auth.paper.adapter.inbound.staff;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.application.staff.AccountInfo;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * {@code /uxmauth info} and {@code /uxmauth history}: an account as staff read it, in lines of the catalogue. The
 * tables are read on the lane, and the lines are sent on the sender's own thread.
 */
public final class StaffView implements Staff {

    private final AccountInfo info;
    private final HashingLane lane;
    private final Replies replies;
    private final Messages messages;
    private final Supplier<DateTimeFormatter> dates;

    /** @param dates how a date reads to staff, in the zone it is shown in */
    public StaffView(
            AccountInfo info, HashingLane lane, Replies replies, Messages messages, Supplier<DateTimeFormatter> dates) {
        this.info = Objects.requireNonNull(info, "info");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.dates = Objects.requireNonNull(dates, "dates");
    }

    @Override
    public CompletableFuture<Void> info(CommandSender sender, String name) {
        Objects.requireNonNull(sender, "sender");
        PlayerName asked = new PlayerName(Objects.requireNonNull(name, "name"));
        return replies.on(sender, lane.submit(() -> info.of(asked)), view -> {
            if (view.isEmpty()) {
                messages.send(sender, Keys.STAFF_UNKNOWN, Text.placeholder("name", name));
                return;
            }
            lines(sender, view.get()).forEach(sender::sendMessage);
        });
    }

    @Override
    public CompletableFuture<Void> history(CommandSender sender, String name) {
        Objects.requireNonNull(sender, "sender");
        PlayerName asked = new PlayerName(Objects.requireNonNull(name, "name"));
        return replies.on(sender, lane.submit(() -> info.history(asked, Integer.MAX_VALUE)), logins -> {
            if (logins.isEmpty()) {
                messages.send(sender, Keys.STAFF_UNKNOWN, Text.placeholder("name", name));
                return;
            }
            TagResolver who = Text.placeholder("name", name);
            if (logins.get().isEmpty()) {
                messages.send(sender, Keys.HISTORY_EMPTY, who);
                return;
            }
            messages.send(sender, Keys.HISTORY_HEADER, who);
            logins.get().forEach(login -> sender.sendMessage(line(sender, login)));
        });
    }

    private List<Component> lines(CommandSender sender, AccountInfo.View view) {
        Account account = view.account();
        return List.of(
                messages.render(
                        sender,
                        Keys.INFO_HEADER,
                        Text.placeholder("name", account.name().value()),
                        Text.component("kind", messages.render(sender, kind(account.kind())))),
                messages.render(
                        sender,
                        Keys.INFO_REGISTERED,
                        Text.placeholder("date", date(account.registeredAt())),
                        Text.placeholder("address", account.registrationAddress())),
                lastLogin(sender, view),
                messages.render(
                        sender,
                        Keys.INFO_FACTORS,
                        Text.component("app", onOff(sender, view.app())),
                        Text.placeholder("codes", String.valueOf(view.backupCodes())),
                        Text.component("mail", onOff(sender, view.mailedCode()))),
                view.address()
                        .map(address ->
                                messages.render(sender, Keys.INFO_ADDRESS, Text.placeholder("address", address)))
                        .orElseGet(() -> messages.render(sender, Keys.INFO_NO_ADDRESS)),
                alts(sender, view));
    }

    /** The newest login the history keeps, else the one the account row last wrote, else none. */
    private Component lastLogin(CommandSender sender, AccountInfo.View view) {
        Account account = view.account();
        Optional<LoginHistory.Entry> newest = view.recent().stream().findFirst();
        Instant at = newest.map(LoginHistory.Entry::at).orElse(account.lastLoginAt());
        if (at == null) {
            return messages.render(sender, Keys.INFO_NEVER);
        }
        String address = newest.map(LoginHistory.Entry::address)
                .orElseGet(() -> Optional.ofNullable(account.lastAddress()).orElse(""));
        return messages.render(
                sender,
                Keys.INFO_LAST_LOGIN,
                Text.placeholder("date", date(at)),
                Text.placeholder("address", address),
                Text.placeholder("logins", String.valueOf(view.logins())));
    }

    private Component alts(CommandSender sender, AccountInfo.View view) {
        if (view.alts().isEmpty()) {
            return messages.render(sender, Keys.INFO_NO_ALTS);
        }
        String names =
                String.join(", ", view.alts().stream().map(PlayerName::value).toList());
        return messages.render(sender, Keys.INFO_ALTS, Text.placeholder("alts", names));
    }

    private Component line(CommandSender sender, LoginHistory.Entry login) {
        return messages.render(
                sender,
                Keys.HISTORY_LINE,
                Text.placeholder("date", date(login.at())),
                Text.placeholder("address", login.address()),
                Text.component("method", messages.render(sender, method(login.method()))));
    }

    private Component onOff(CommandSender sender, boolean on) {
        return messages.render(sender, on ? Keys.INFO_ON : Keys.INFO_OFF);
    }

    private String date(Instant at) {
        return dates.get().format(at);
    }

    private static Keys kind(AccountKind kind) {
        return switch (kind) {
            case CRACKED -> Keys.INFO_KIND_CRACKED;
            case PREMIUM -> Keys.INFO_KIND_PREMIUM;
            case BEDROCK -> Keys.INFO_KIND_BEDROCK;
        };
    }

    /** The word of a way in, which the history and the menu both read. */
    static Keys method(AuthMethod method) {
        return switch (method) {
            case PASSWORD -> Keys.METHOD_PASSWORD;
            case REGISTERED -> Keys.METHOD_REGISTERED;
            case PREMIUM -> Keys.METHOD_PREMIUM;
            case BEDROCK -> Keys.METHOD_BEDROCK;
            case SESSION -> Keys.METHOD_SESSION;
            case FORCED -> Keys.METHOD_FORCED;
            case NETWORK -> Keys.METHOD_NETWORK;
        };
    }
}
