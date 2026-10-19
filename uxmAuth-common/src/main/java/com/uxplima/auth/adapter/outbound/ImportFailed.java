package com.uxplima.auth.adapter.outbound;

/**
 * The import config.conf names failed. The start stops here, nothing is marked complete, and the message says
 * which source failed and why.
 */
public final class ImportFailed extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public ImportFailed(String message, Throwable cause) {
        super(message, cause);
    }
}
