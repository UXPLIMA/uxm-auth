package com.uxplima.auth.application.mail;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.auth.application.factor.FactorCheck;
import com.uxplima.auth.application.port.Emails;

/**
 * The login code mailed to an account, as the second step takes it: only while the account takes its code by mail,
 * so a code left out when the mail was turned off answers nothing. Runs on the hashing lane, as the check does.
 */
public final class MailedLoginCode implements FactorCheck.MailedCode {

    private final Emails emails;
    private final MailCodes codes;

    public MailedLoginCode(Emails emails, MailCodes codes) {
        this.emails = Objects.requireNonNull(emails, "emails");
        this.codes = Objects.requireNonNull(codes, "codes");
    }

    @Override
    public boolean takes(UUID account, String typed) {
        return emails.mailsLoginCode(account) && codes.takes(account, Emails.Purpose.LOGIN, typed);
    }
}
