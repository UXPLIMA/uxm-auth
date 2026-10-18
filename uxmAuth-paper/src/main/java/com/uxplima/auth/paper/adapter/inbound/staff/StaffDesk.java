package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.auth.adapter.outbound.PasswordWords;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.protection.AddressBans;
import com.uxplima.auth.application.staff.FactorOff;
import com.uxplima.auth.application.staff.ForceLogin;
import com.uxplima.auth.application.staff.ForceRegister;
import com.uxplima.auth.application.staff.MarkKind;
import com.uxplima.auth.application.staff.SetPassword;
import com.uxplima.auth.application.staff.StaffAction;
import com.uxplima.auth.application.staff.Unregister;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * What staff do to an account from {@code /uxmauth}. Each passes the gate first, runs on the lane because every one
 * reads or writes a table, answers the sender on their own thread, and leaves one line in the console.
 */
public final class StaffDesk implements StaffActions {

    /** The use cases staff reach through the desk. */
    public record Cases(
            ForceLogin force,
            AddressBans bans,
            Unregister unregister,
            SetPassword setPassword,
            ForceRegister register,
            FactorOff factorOff,
            MarkKind marks) {
        public Cases {
            Objects.requireNonNull(force, "force");
            Objects.requireNonNull(bans, "bans");
            Objects.requireNonNull(unregister, "unregister");
            Objects.requireNonNull(setPassword, "setPassword");
            Objects.requireNonNull(register, "register");
            Objects.requireNonNull(factorOff, "factorOff");
            Objects.requireNonNull(marks, "marks");
        }
    }

    /**
     * What the desk reaches on this server: the player online under a name, and what a made account means to them.
     *
     * @param online the id of the player online here under exactly this name, when there is one
     * @param registered tells a player waiting under a made account's name to log in
     */
    public record Reach(Function<String, Optional<UUID>> online, Consumer<Account> registered) {
        public Reach {
            Objects.requireNonNull(online, "online");
            Objects.requireNonNull(registered, "registered");
        }
    }

    private final StaffGate gate;
    private final Cases cases;
    private final Reach reach;
    private final HashingLane lane;
    private final Replies replies;
    private final Messages messages;
    private final Consumer<String> log;

    /** @param log where the line each action leaves goes, which is the console */
    public StaffDesk(
            StaffGate gate,
            Cases cases,
            Reach reach,
            HashingLane lane,
            Replies replies,
            Messages messages,
            Consumer<String> log) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.cases = Objects.requireNonNull(cases, "cases");
        this.reach = Objects.requireNonNull(reach, "reach");
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
        return replies.on(sender, lane.submit(() -> cases.force().ask(new PlayerName(name))), asked -> {
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
        return replies.on(sender, lane.submit(() -> cases.bans().unban(address)), unban -> {
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

    @Override
    public CompletableFuture<Void> unregister(CommandSender sender, String name) {
        Objects.requireNonNull(name, "name");
        if (!gate.allows(Objects.requireNonNull(sender, "sender"), StaffAction.UNREGISTER)) {
            return CompletableFuture.completedFuture(null);
        }
        return replies.on(sender, lane.submit(() -> cases.unregister().remove(new PlayerName(name))), removed -> {
            if (removed == Unregister.Removed.UNKNOWN) {
                messages.send(sender, Keys.STAFF_UNKNOWN, Text.placeholder("name", name));
                return;
            }
            messages.send(sender, Keys.STAFF_REMOVED, Text.placeholder("name", name));
            log.accept(sender.getName() + " removed the account of " + name + ".");
        });
    }

    @Override
    public CompletableFuture<Void> setPassword(CommandSender sender, String name, String password) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(password, "password");
        if (!gate.allows(Objects.requireNonNull(sender, "sender"), StaffAction.SET_PASSWORD)) {
            return CompletableFuture.completedFuture(null);
        }
        Password fresh = Password.of(password);
        return replies.on(sender, lane.submit(() -> cases.setPassword().set(new PlayerName(name), fresh)), outcome -> {
            TagResolver who = Text.placeholder("name", name);
            switch (outcome) {
                case SetPassword.Outcome.Set set -> {
                    messages.send(sender, Keys.STAFF_PASSWORD_SET, who);
                    log.accept(sender.getName() + " set the password of " + name + ".");
                }
                case SetPassword.Outcome.Rejected rejected ->
                    PasswordWords.reason(messages, sender, rejected.verdict()).ifPresent(sender::sendMessage);
                case SetPassword.Outcome.ChangedMeanwhile raced ->
                    messages.send(sender, Keys.STAFF_PASSWORD_RACED, who);
                case SetPassword.Outcome.Unknown unknown -> messages.send(sender, Keys.STAFF_UNKNOWN, who);
            }
        });
    }

    @Override
    public CompletableFuture<Void> forceRegister(CommandSender sender, String name, String password) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(password, "password");
        if (!gate.allows(Objects.requireNonNull(sender, "sender"), StaffAction.FORCE_REGISTER)) {
            return CompletableFuture.completedFuture(null);
        }
        Password fresh = Password.of(password);
        Optional<UUID> online = reach.online().apply(name);
        return replies.on(sender, lane.submit(() -> cases.register().register(name, fresh, online)), outcome -> {
            TagResolver who = Text.placeholder("name", name);
            switch (outcome) {
                case ForceRegister.Outcome.Made made -> {
                    messages.send(sender, Keys.STAFF_REGISTERED, who);
                    log.accept(sender.getName() + " made an account for " + name + ".");
                    reach.registered().accept(made.account());
                }
                case ForceRegister.Outcome.Taken taken -> messages.send(sender, Keys.STAFF_NAME_TAKEN, who);
                case ForceRegister.Outcome.NameRefused refused -> messages.send(sender, Keys.STAFF_NAME_REFUSED, who);
                case ForceRegister.Outcome.Rejected rejected ->
                    PasswordWords.reason(messages, sender, rejected.verdict()).ifPresent(sender::sendMessage);
            }
        });
    }

    @Override
    public CompletableFuture<Void> disableFactor(CommandSender sender, String name) {
        Objects.requireNonNull(name, "name");
        if (!gate.allows(Objects.requireNonNull(sender, "sender"), StaffAction.DISABLE_2FA)) {
            return CompletableFuture.completedFuture(null);
        }
        return replies.on(sender, lane.submit(() -> cases.factorOff().off(new PlayerName(name))), off -> {
            TagResolver who = Text.placeholder("name", name);
            switch (off) {
                case OFF -> {
                    messages.send(sender, Keys.STAFF_FACTOR_OFF, who);
                    log.accept(sender.getName() + " turned off every second factor of " + name + ".");
                }
                case NONE -> messages.send(sender, Keys.STAFF_FACTOR_NONE, who);
                case UNKNOWN -> messages.send(sender, Keys.STAFF_UNKNOWN, who);
            }
        });
    }

    @Override
    public CompletableFuture<Void> markPremium(CommandSender sender, String name) {
        return mark(sender, name, StaffAction.MARK_PREMIUM, Keys.STAFF_MARKED_PREMIUM, "premium");
    }

    @Override
    public CompletableFuture<Void> markCracked(CommandSender sender, String name) {
        return mark(sender, name, StaffAction.MARK_CRACKED, Keys.STAFF_MARKED_CRACKED, "cracked");
    }

    /** Marks the account one kind or the other; asking Mojang may take a while, so it runs on the lane. */
    private CompletableFuture<Void> mark(
            CommandSender sender, String name, StaffAction action, Keys done, String kind) {
        Objects.requireNonNull(name, "name");
        if (!gate.allows(Objects.requireNonNull(sender, "sender"), action)) {
            return CompletableFuture.completedFuture(null);
        }
        PlayerName player = new PlayerName(name);
        boolean premium = action == StaffAction.MARK_PREMIUM;
        return replies.on(
                sender,
                lane.submit(() ->
                        premium ? cases.marks().premium(player) : cases.marks().cracked(player)),
                marked -> {
                    TagResolver who = Text.placeholder("name", name);
                    messages.send(sender, marked == MarkKind.Marked.DONE ? done : markWord(marked), who);
                    if (marked == MarkKind.Marked.DONE) {
                        log.accept(sender.getName() + " marked " + name + " " + kind + ".");
                    }
                });
    }

    private static Keys markWord(MarkKind.Marked marked) {
        return switch (marked) {
            case DONE, ALREADY -> Keys.STAFF_MARK_ALREADY;
            case NOT_PAID -> Keys.STAFF_NOT_PAID;
            case NO_ANSWER -> Keys.STAFF_NO_ANSWER;
            case TAKEN -> Keys.STAFF_PAID_TAKEN;
            case NO_PASSWORD -> Keys.STAFF_NO_PASSWORD;
            case UNKNOWN -> Keys.STAFF_UNKNOWN;
        };
    }
}
