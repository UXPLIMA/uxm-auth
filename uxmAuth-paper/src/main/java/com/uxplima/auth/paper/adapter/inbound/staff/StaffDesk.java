package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.command.CommandSender;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.protection.AddressBans;
import com.uxplima.auth.application.staff.ForceLogin;
import com.uxplima.auth.application.staff.StaffAction;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * What staff do to an account from {@code /uxmauth}. Each passes the gate first, runs on the lane because every one
 * reads or writes a table, answers the sender on their own thread, and leaves one line in the console.
 */
public final class StaffDesk implements StaffActions {

    private final StaffGate gate;
    private final ForceLogin force;
    private final AddressBans bans;
    private final HashingLane lane;
    private final Replies replies;
    private final Messages messages;
    private final Consumer<String> log;

    /** @param log where the line each action leaves goes, which is the console */
    public StaffDesk(
            StaffGate gate,
            ForceLogin force,
            AddressBans bans,
            HashingLane lane,
            Replies replies,
            Messages messages,
            Consumer<String> log) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.force = Objects.requireNonNull(force, "force");
        this.bans = Objects.requireNonNull(bans, "bans");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public CompletableFuture<Void> forceLogin(CommandSender sender, String name) {
        Objects.requireNonNull(name, "name");
        if (!gate.allows(Objects.requireNonNull(sender, "sender"), StaffAction.FORCE_LOGIN)) {
            return CompletableFuture.completedFuture(null);
        }
        return replies.on(sender, lane.submit(() -> force.ask(new PlayerName(name))), asked -> {
            if (asked == ForceLogin.Asked.UNKNOWN) {
                messages.send(sender, Keys.STAFF_UNKNOWN, Text.placeholder("name", name));
                return;
            }
            messages.send(sender, Keys.STAFF_FORCED_LOGIN, Text.placeholder("name", name));
            log.accept(sender.getName() + " let " + name + " in past the login.");
        });
    }

    @Override
    public CompletableFuture<Void> unban(CommandSender sender, String address) {
        Objects.requireNonNull(address, "address");
        if (!gate.allows(Objects.requireNonNull(sender, "sender"), StaffAction.UNBAN)) {
            return CompletableFuture.completedFuture(null);
        }
        return replies.on(sender, lane.submit(() -> bans.unban(address)), unban -> {
            messages.send(
                    sender,
                    switch (unban) {
                        case LIFTED -> Keys.STAFF_UNBANNED;
                        case NOT_BANNED -> Keys.STAFF_NOT_BANNED;
                        case NOT_AN_ADDRESS -> Keys.STAFF_NOT_AN_ADDRESS;
                    },
                    Text.placeholder("address", address));
            if (unban != AddressBans.Unban.NOT_AN_ADDRESS) {
                log.accept(sender.getName() + " lifted any ban on " + address + ".");
            }
        });
    }
}
