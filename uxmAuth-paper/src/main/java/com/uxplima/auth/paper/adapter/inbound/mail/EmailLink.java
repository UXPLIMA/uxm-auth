package com.uxplima.auth.paper.adapter.inbound.mail;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.TimeWords;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.mail.LinkEmail;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.command.Addresses;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Linking an address in the world. The address gets a code and is saved when the code comes back; it is only ever
 * shown masked. A Bedrock player types the address and then the code into forms. The use case reads the table and
 * hashes the code on the hashing lane; the player is answered on their own thread.
 */
public final class EmailLink {

    /** The Bedrock forms: one field for the address, then one for the code. */
    public interface Forms {
        /** No form: chat is all there is. */
        Forms NONE = new Forms() {
            @Override
            public void address(Player player, Consumer<String> answer) {}

            @Override
            public void code(Player player, Consumer<String> answer) {}
        };

        void address(Player player, Consumer<String> answer);

        void code(Player player, Consumer<String> answer);
    }

    private final LinkEmail link;
    private final Sessions sessions;
    private final Forms forms;
    private final Predicate<UUID> bedrock;
    private final HashingLane lane;
    private final Replies replies;
    private final MomentRunner moments;
    private final Messages messages;
    private final TimeWords times;

    /** @param lane where the table is read and codes hashed, never a server or region thread */
    public EmailLink(
            LinkEmail link,
            Sessions sessions,
            Forms forms,
            Predicate<UUID> bedrock,
            HashingLane lane,
            Replies replies,
            MomentRunner moments,
            Messages messages) {
        this.link = Objects.requireNonNull(link, "link");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.forms = Objects.requireNonNull(forms, "forms");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.moments = Objects.requireNonNull(moments, "moments");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.times = new TimeWords(messages);
    }

    /** {@code /email} alone: what it does and the address linked now; a Bedrock player gets the address form. */
    public CompletableFuture<Void> usage(Player player) {
        Objects.requireNonNull(player, "player");
        Optional<Account> account = account(player);
        if (account.isEmpty()) {
            return done();
        }
        UUID id = account.get().id();
        return replies.on(player, lane.submit(() -> link.linked(id)), linked -> {
            messages.send(player, Keys.EMAIL_USAGE);
            linked.ifPresent(address -> messages.send(player, Keys.EMAIL_LINKED, masked(address.masked())));
            if (bedrock.test(player.getUniqueId())) {
                forms.address(player, typed -> {
                    CompletableFuture<Void> unused = add(player, typed);
                });
            }
        });
    }

    /** {@code /email add <address>}: a code goes to it. */
    public CompletableFuture<Void> add(Player player, String typed) {
        Objects.requireNonNull(typed, "typed");
        return run(player, account -> link.add(account.id(), account.name(), typed, messages.localeOf(player)));
    }

    /** {@code /email confirm <code>}: the code that went to the address links it; a wrong one counts against it. */
    public CompletableFuture<Void> confirm(Player player, String code) {
        Objects.requireNonNull(code, "code");
        String address = Addresses.of(player);
        String host =
                sessions.held(player.getUniqueId()).map(Sessions.Held::host).orElse("");
        return run(player, account -> link.confirm(account.id(), code, address, host));
    }

    /** {@code /email remove}: the linked address goes. */
    public CompletableFuture<Void> remove(Player player) {
        return run(player, account -> link.remove(account.id()));
    }

    private CompletableFuture<Void> run(Player player, Function<Account, CompletableFuture<LinkEmail.Outcome>> work) {
        Objects.requireNonNull(player, "player");
        Optional<Account> account = account(player);
        if (account.isEmpty()) {
            return done();
        }
        Account in = account.get();
        return replies.on(player, work.apply(in), outcome -> answer(player, outcome));
    }

    private void answer(Player player, LinkEmail.Outcome outcome) {
        switch (outcome) {
            case LinkEmail.Outcome.Sent sent -> sent(player, sent);
            case LinkEmail.Outcome.Cooldown cooldown ->
                messages.send(
                        player,
                        Keys.EMAIL_COOLDOWN,
                        Text.placeholder(
                                "seconds",
                                String.valueOf(Math.max(1, cooldown.left().toSeconds()))));
            case LinkEmail.Outcome.Confirmed confirmed ->
                messages.send(player, Keys.EMAIL_CONFIRMED, masked(confirmed.masked()));
            case LinkEmail.Outcome.Wrong wrong ->
                messages.send(player, Keys.EMAIL_WRONG, Text.placeholder("left", String.valueOf(wrong.attemptsLeft())));
            case LinkEmail.Outcome.AlreadyLinked linked ->
                messages.send(player, Keys.EMAIL_ALREADY_LINKED, masked(linked.masked()));
            case LinkEmail.Outcome.Removed removed ->
                messages.send(player, Keys.EMAIL_REMOVED, masked(removed.masked()));
            case LinkEmail.Outcome.Banned banned -> {
                moments.fire(player, AuthSettings.Moment.BANNED);
                player.kick(messages.render(player, Keys.KICK_BANNED, times.placeholder(player, banned.lasts())));
            }
            default -> messages.send(player, plain(outcome));
        }
    }

    /** The answers that carry nothing but their words. */
    private static Keys plain(LinkEmail.Outcome outcome) {
        return switch (outcome) {
            case LinkEmail.Outcome.NotAnAddress none -> Keys.EMAIL_NOT_AN_ADDRESS;
            case LinkEmail.Outcome.DomainRefused refused -> Keys.EMAIL_DOMAIN_REFUSED;
            case LinkEmail.Outcome.MailOff off -> Keys.EMAIL_MAIL_OFF;
            case LinkEmail.Outcome.MailFailed failed -> Keys.EMAIL_MAIL_FAILED;
            case LinkEmail.Outcome.Expired expired -> Keys.EMAIL_EXPIRED;
            case LinkEmail.Outcome.NothingPending nothing -> Keys.EMAIL_NOTHING_PENDING;
            case LinkEmail.Outcome.TooManyAttempts tooMany -> Keys.EMAIL_TOO_MANY_ATTEMPTS;
            case LinkEmail.Outcome.NoneLinked none -> Keys.EMAIL_NONE_LINKED;
            default -> throw new IllegalStateException("an answer with words of its own: " + outcome);
        };
    }

    private void sent(Player player, LinkEmail.Outcome.Sent sent) {
        messages.send(
                player,
                Keys.EMAIL_SENT,
                masked(sent.masked()),
                Text.placeholder("minutes", String.valueOf(sent.lasts().toMinutes())));
        if (bedrock.test(player.getUniqueId())) {
            forms.code(player, code -> {
                CompletableFuture<Void> unused = confirm(player, code);
            });
        }
    }

    private static TagResolver masked(String address) {
        return Text.placeholder("address", address);
    }

    /** The account of a logged-in player, or the refusal told. */
    private Optional<Account> account(Player player) {
        if (sessions.state(player.getUniqueId()).orElse(null) instanceof AuthState.Authenticated in) {
            return Optional.of(in.account());
        }
        messages.send(player, Keys.EMAIL_LOG_IN_FIRST);
        return Optional.empty();
    }

    private static CompletableFuture<Void> done() {
        return CompletableFuture.completedFuture(null);
    }
}
