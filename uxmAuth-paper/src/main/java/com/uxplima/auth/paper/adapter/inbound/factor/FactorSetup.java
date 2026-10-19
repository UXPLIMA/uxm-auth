package com.uxplima.auth.paper.adapter.inbound.factor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.TimeWords;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.factor.SetUpTotp;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.factor.BackupCodes;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.command.Addresses;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Turning the app on and off in the world. A setup tells the key and the link in chat, each to click and copy,
 * puts the QR code on a map where it takes no item's place, and shows a Bedrock player a form with the key to copy
 * and a field for the first code. Backup codes are told once in chat to click and copy, and in a form to a Bedrock
 * player. The table is read and written, and backup codes hashed, on the hashing lane; the player is answered on
 * their own thread.
 */
public final class FactorSetup {

    /** Where the QR code of the link goes, and comes back from. */
    public interface Hand {
        /** No map: the key and the link are all there is. */
        Hand NONE = new Hand() {
            @Override
            public boolean give(Player player, String link) {
                return false;
            }

            @Override
            public void take(Player player) {}
        };

        /** Says whether the map with this link's QR code is in the player's hand now. */
        boolean give(Player player, String link);

        /** Takes every copy of the map back, and forgets the picture. */
        void take(Player player);
    }

    /** The Bedrock forms of a setup. */
    public interface Form {
        /** No form: chat is all there is. */
        Form NONE = new Form() {
            @Override
            public void show(Player player, String key, int digits, Consumer<String> answer) {}

            @Override
            public void codes(Player player, List<String> codes) {}
        };

        /** The key to copy, and a field whose code comes back to {@code answer}. */
        void show(Player player, String key, int digits, Consumer<String> answer);

        /** The backup codes, as they are shown, to copy. */
        void codes(Player player, List<String> codes);
    }

    private final SetUpTotp setUp;
    private final Sessions sessions;
    private final OwedSetups owed;
    private final Supplier<SetUpTotp.Settings> settings;
    private final Hand hand;
    private final Form form;
    private final Predicate<UUID> bedrock;
    private final HashingLane lane;
    private final Replies replies;
    private final MomentRunner moments;
    private final Messages messages;
    private final TimeWords times;

    /** @param lane where the table is read and written and backup codes hashed, never a server or region thread */
    public FactorSetup(
            SetUpTotp setUp,
            Sessions sessions,
            OwedSetups owed,
            Supplier<SetUpTotp.Settings> settings,
            Hand hand,
            Form form,
            Predicate<UUID> bedrock,
            HashingLane lane,
            Replies replies,
            MomentRunner moments,
            Messages messages) {
        this.setUp = Objects.requireNonNull(setUp, "setUp");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.owed = Objects.requireNonNull(owed, "owed");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.hand = Objects.requireNonNull(hand, "hand");
        this.form = Objects.requireNonNull(form, "form");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.moments = Objects.requireNonNull(moments, "moments");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.times = new TimeWords(messages);
    }

    /** {@code /2fa} alone: what the command does. */
    public void usage(Player player) {
        messages.send(Objects.requireNonNull(player, "player"), Keys.FACTOR_USAGE);
    }

    /** {@code /2fa setup}: a fresh key, its link and its QR code, answering when the player has been told. */
    public CompletableFuture<Void> setup(Player player) {
        Objects.requireNonNull(player, "player");
        Optional<Account> account = account(player);
        if (account.isEmpty()) {
            return done();
        }
        Account in = account.get();
        return replies.on(
                player, lane.submit(() -> setUp.start(in.id(), in.name())), outcome -> answer(player, outcome));
    }

    /** {@code /2fa confirm <code>}: the first code from the app, which turns it on. */
    public CompletableFuture<Void> confirm(Player player, String code) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(code, "code");
        if (code.isEmpty()) {
            usage(player);
            return done();
        }
        Optional<Account> account = account(player);
        if (account.isEmpty()) {
            return done();
        }
        UUID id = account.get().id();
        return replies.on(player, lane.submit(() -> setUp.confirm(id, code)), outcome -> answer(player, outcome));
    }

    /** {@code /2fa off <code>}: a code from the app or a backup code turns it off; a wrong one counts against the address. */
    public CompletableFuture<Void> off(Player player, String code) {
        return proven(player, code, setUp::off);
    }

    /** {@code /2fa backup <code>}: a code from the app or a backup code buys a new set of backup codes. */
    public CompletableFuture<Void> backup(Player player, String code) {
        return proven(player, code, setUp::backup);
    }

    /** What a code proves, from the logged-in player and the address and host they came by. */
    @FunctionalInterface
    private interface Proof {
        SetUpTotp.Outcome run(UUID account, String code, String address, String host);
    }

    private CompletableFuture<Void> proven(Player player, String code, Proof proof) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(code, "code");
        // A bare label is a question: as an answer it would count as a wrong code against the address.
        if (code.isEmpty()) {
            usage(player);
            return done();
        }
        Optional<Account> account = account(player);
        if (account.isEmpty()) {
            return done();
        }
        UUID id = account.get().id();
        String address = Addresses.of(player);
        String host =
                sessions.held(player.getUniqueId()).map(Sessions.Held::host).orElse("");
        return replies.on(
                player, lane.submit(() -> proof.run(id, code, address, host)), outcome -> answer(player, outcome));
    }

    /** The player left: an open setup goes, and so does the map, and a hold for the app ends with them. */
    public void left(Player player) {
        Objects.requireNonNull(player, "player");
        owed.forget(player.getUniqueId());
        account(player, false).ifPresent(account -> setUp.forget(account.id()));
        hand.take(player);
    }

    private void answer(Player player, SetUpTotp.Outcome outcome) {
        switch (outcome) {
            case SetUpTotp.Outcome.Started started -> started(player, started);
            case SetUpTotp.Outcome.On on -> {
                hand.take(player);
                messages.send(player, Keys.FACTOR_ON);
                codes(player, on.backupCodes());
                owed.release(player);
            }
            case SetUpTotp.Outcome.NewCodes fresh -> codes(player, fresh.codes());
            case SetUpTotp.Outcome.Off off -> messages.send(player, Keys.FACTOR_OFF);
            case SetUpTotp.Outcome.Wrong wrong -> messages.send(player, Keys.FACTOR_WRONG);
            case SetUpTotp.Outcome.Banned banned -> {
                moments.fire(player, AuthSettings.Moment.BANNED);
                player.kick(messages.render(player, Keys.KICK_BANNED, times.placeholder(player, banned.time())));
            }
            case SetUpTotp.Outcome.NothingStarted nothing -> {
                hand.take(player);
                messages.send(player, Keys.FACTOR_NOTHING_STARTED);
            }
            case SetUpTotp.Outcome.AlreadyOn already -> messages.send(player, Keys.FACTOR_ALREADY_ON);
            case SetUpTotp.Outcome.NotOn notOn -> messages.send(player, Keys.FACTOR_NOT_ON);
        }
    }

    private void started(Player player, SetUpTotp.Outcome.Started started) {
        String key = started.secret().base32();
        messages.send(player, Keys.FACTOR_SECRET, Text.component("secret", copied(key)));
        messages.send(player, Keys.FACTOR_LINK, Text.component("link", copied(started.link())));
        messages.send(player, hand.give(player, started.link()) ? Keys.FACTOR_MAP : Keys.FACTOR_NO_ROOM);
        SetUpTotp.Settings now = settings.get();
        messages.send(
                player,
                Keys.FACTOR_CONFIRM,
                Text.placeholder("minutes", String.valueOf(now.setupTime().toMinutes())));
        if (bedrock.test(player.getUniqueId())) {
            form.show(player, key, now.totp().digits(), code -> {
                CompletableFuture<Void> unused = confirm(player, code);
            });
        }
    }

    /** The backup codes in chat to click and copy all at once, and in a form to a Bedrock player. */
    private void codes(Player player, List<String> codes) {
        List<String> shown = codes.stream().map(BackupCodes::shown).toList();
        messages.send(player, Keys.FACTOR_BACKUP_CODES, Text.component("codes", copied(String.join(" ", shown))));
        if (bedrock.test(player.getUniqueId())) {
            form.codes(player, shown);
        }
    }

    private static Component copied(String text) {
        return Component.text(text).clickEvent(ClickEvent.copyToClipboard(text));
    }

    /** The account of a logged-in player, or of one held for the app, or the reason they are not told as a refusal. */
    private Optional<Account> account(Player player) {
        return account(player, true);
    }

    private Optional<Account> account(Player player, boolean tell) {
        AuthState state = sessions.state(player.getUniqueId()).orElse(null);
        if (state instanceof AuthState.Authenticated in) {
            return Optional.of(in.account());
        }
        if (state instanceof AuthState.SettingUp held) {
            return Optional.of(held.account());
        }
        if (tell) {
            messages.send(player, Keys.FACTOR_LOG_IN_FIRST);
        }
        return Optional.empty();
    }

    private static CompletableFuture<Void> done() {
        return CompletableFuture.completedFuture(null);
    }
}
