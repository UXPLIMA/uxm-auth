package com.uxplima.auth.application.port;

import java.util.UUID;
import java.util.function.UnaryOperator;

import com.uxplima.auth.domain.mail.SendTally;

/**
 * How often codes of each kind went to each account, shared by a whole network and kept when the codes themselves
 * are gone, so the cooldown and the day's cap hold whatever became of the code.
 */
public interface MailSends {

    /**
     * Changes the account's tally of this kind in one step against every other writer, starting from none the
     * first time, and answers what it became. The change may run more than once; it must do nothing else.
     */
    SendTally change(UUID account, Emails.Purpose purpose, UnaryOperator<SendTally> change);
}
