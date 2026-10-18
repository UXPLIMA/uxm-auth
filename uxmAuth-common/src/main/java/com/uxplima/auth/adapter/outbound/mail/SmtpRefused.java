package com.uxplima.auth.adapter.outbound.mail;

import java.io.IOException;

/**
 * The mail server would not go on. It names the step and the reply code only: what the server said may quote the
 * address, and this message reaches the console.
 */
public final class SmtpRefused extends IOException {

    private static final long serialVersionUID = 1L;

    private final int code;
    private final String step;

    SmtpRefused(String step, int code) {
        super("the mail server answered " + code + " at " + step);
        this.step = step;
        this.code = code;
    }

    SmtpRefused(String step, String why) {
        super("the mail server cannot be used at " + step + ": " + why);
        this.step = step;
        this.code = 0;
    }

    /** The reply code, or 0 when this client stopped on its own. */
    public int code() {
        return code;
    }

    /** The command the server refused, as {@code RCPT}. */
    public String step() {
        return step;
    }
}
