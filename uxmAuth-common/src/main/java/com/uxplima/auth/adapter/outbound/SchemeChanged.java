package com.uxplima.auth.adapter.outbound;

/**
 * config.conf names another UUID scheme than the one the accounts were made under. The start stops here, and
 * the message says which line to put back and which command moves the accounts instead.
 */
public final class SchemeChanged extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public SchemeChanged(String message) {
        super(message);
    }
}
