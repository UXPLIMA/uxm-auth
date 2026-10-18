package com.uxplima.auth.application.mail;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Mailer;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.mail.EmailAddress;

/**
 * The login code by mail, sent as the second step opens. A code still good is not replaced, so a player who
 * reconnects types the code they already have. The table is read here, so this runs off the server thread.
 */
public final class MailLogin {

    private final Emails emails;
    private final MailCodes codes;

    public MailLogin(Emails emails, MailCodes codes) {
        this.emails = Objects.requireNonNull(emails, "emails");
        this.codes = Objects.requireNonNull(codes, "codes");
    }

    /** Mails the login code to the account's linked address, in this language, unless one still good is out. */
    public CompletableFuture<MailCodes.Issued> send(Account account, Locale language) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(language, "language");
        Optional<EmailAddress> to = emails.address(account.id());
        if (to.isEmpty()) {
            return CompletableFuture.completedFuture(new MailCodes.Issued.Off());
        }
        return codes.issueUnlessLive(
                account.id(),
                Emails.Purpose.LOGIN,
                to.get(),
                Mailer.Kind.LOGIN_CODE,
                language,
                Map.of("player", account.name().value()));
    }
}
