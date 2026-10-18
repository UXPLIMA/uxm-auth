package com.uxplima.auth.application.mail;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Mailer;
import com.uxplima.auth.domain.mail.DomainRule;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.name.PlayerName;

/**
 * A player links an address. A code goes to it, and only that code saves it, in place of any address before; an
 * address is only ever shown back masked. The table is read on the hashing lane, never the caller's thread.
 */
public final class LinkEmail {

    /** What linking, confirming or removing came to. */
    public sealed interface Outcome {
        /** A code went to the address, shown masked, good for this long. */
        record Sent(String masked, Duration lasts) implements Outcome {}

        /** The text is not an address. */
        record NotAnAddress() implements Outcome {}

        /** The operator takes no address at that domain. */
        record DomainRefused() implements Outcome {}

        /** This address is linked already. */
        record AlreadyLinked(String masked) implements Outcome {}

        /** A code went out too recently; another may go after this long. */
        record Cooldown(Duration left) implements Outcome {}

        /** Mail is off on this server. */
        record MailOff() implements Outcome {}

        /** The mail could not be sent. */
        record MailFailed() implements Outcome {}

        /** The code passed: this address is linked now. */
        record Confirmed(String masked) implements Outcome {}

        /** Not the code, with this many tries left. */
        record Wrong(int attemptsLeft) implements Outcome {}

        /** The code is past its time. */
        record Expired() implements Outcome {}

        /** No code waits. */
        record NothingPending() implements Outcome {}

        /** The last try is spent, and the code with it. */
        record TooManyAttempts() implements Outcome {}

        /** The wrong code banned the address it came from, for this long. */
        record Banned(Duration lasts) implements Outcome {}

        /** The linked address is gone. */
        record Removed(String masked) implements Outcome {}

        /** No address is linked. */
        record NoneLinked() implements Outcome {}
    }

    private final Emails emails;
    private final MailCodes codes;
    private final Supplier<DomainRule> domains;
    private final HashingLane lane;

    public LinkEmail(Emails emails, MailCodes codes, Supplier<DomainRule> domains, HashingLane lane) {
        this.emails = Objects.requireNonNull(emails, "emails");
        this.codes = Objects.requireNonNull(codes, "codes");
        this.domains = Objects.requireNonNull(domains, "domains");
        this.lane = Objects.requireNonNull(lane, "lane");
    }

    /** The address the account has linked, read from the table on the caller's thread. */
    public Optional<EmailAddress> linked(UUID account) {
        return emails.address(Objects.requireNonNull(account, "account"));
    }

    /** Sends a code to the typed address, which is linked once the code comes back. */
    public CompletableFuture<Outcome> add(UUID account, PlayerName name, String typed, Locale language) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(language, "language");
        Optional<EmailAddress> parsed = EmailAddress.parse(Objects.requireNonNull(typed, "typed"));
        if (parsed.isEmpty()) {
            return done(new Outcome.NotAnAddress());
        }
        EmailAddress to = parsed.get();
        if (!domains.get().takes(to)) {
            return done(new Outcome.DomainRefused());
        }
        return lane.submit(() -> linked(account).filter(to::equals)).thenCompose(same -> {
            if (same.isPresent()) {
                return done(new Outcome.AlreadyLinked(to.masked()));
            }
            return codes.issue(
                            account,
                            Emails.Purpose.VERIFY,
                            to,
                            Mailer.Kind.VERIFY,
                            language,
                            Map.of("player", name.value()))
                    .thenApply(LinkEmail::issued);
        });
    }

    private static Outcome issued(MailCodes.Issued issued) {
        return switch (issued) {
            case MailCodes.Issued.Sent sent -> new Outcome.Sent(sent.to().masked(), sent.lasts());
            case MailCodes.Issued.Cooldown cooldown -> new Outcome.Cooldown(cooldown.left());
            case MailCodes.Issued.Off off -> new Outcome.MailOff();
            case MailCodes.Issued.Failed failed -> new Outcome.MailFailed();
        };
    }

    /** Takes the code that went to the address, typed from this address, and links it when it is right. */
    public CompletableFuture<Outcome> confirm(UUID account, String code, String address, String host) {
        Objects.requireNonNull(account, "account");
        return codes.check(account, Emails.Purpose.VERIFY, code, address, host)
                .thenApply(checked -> confirmed(account, checked));
    }

    private Outcome confirmed(UUID account, MailCodes.Checked checked) {
        return switch (checked) {
            case MailCodes.Checked.Right right -> {
                emails.setAddress(account, right.to());
                yield new Outcome.Confirmed(right.to().masked());
            }
            case MailCodes.Checked.Wrong wrong -> new Outcome.Wrong(wrong.attemptsLeft());
            case MailCodes.Checked.Expired expired -> new Outcome.Expired();
            case MailCodes.Checked.NothingPending nothing -> new Outcome.NothingPending();
            case MailCodes.Checked.TooManyAttempts tooMany -> new Outcome.TooManyAttempts();
            case MailCodes.Checked.Banned banned -> new Outcome.Banned(banned.lasts());
        };
    }

    /** Takes the linked address away. */
    public CompletableFuture<Outcome> remove(UUID account) {
        Objects.requireNonNull(account, "account");
        return lane.submit(() -> {
            Optional<EmailAddress> linked = linked(account);
            if (linked.isEmpty()) {
                return new Outcome.NoneLinked();
            }
            emails.removeAddress(account);
            return new Outcome.Removed(linked.get().masked());
        });
    }

    private static CompletableFuture<Outcome> done(Outcome outcome) {
        return CompletableFuture.completedFuture(outcome);
    }
}
