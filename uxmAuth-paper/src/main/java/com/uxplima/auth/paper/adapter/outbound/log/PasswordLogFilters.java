package com.uxplima.auth.paper.adapter.outbound.log;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

/**
 * Attaches the password filter to the server's root logger configuration, the one every console line passes,
 * and takes it off again. Attached to the configuration rather than to one logger, it can be removed whole on
 * disable, leaving the server's logging as it was found.
 */
public final class PasswordLogFilters {

    private final PasswordLogFilter filter;
    private boolean installed;

    public PasswordLogFilters(PasswordLogFilter filter) {
        this.filter = Objects.requireNonNull(filter, "filter");
    }

    public void install() {
        if (installed) {
            return;
        }
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        LoggerConfig root = context.getConfiguration().getRootLogger();
        filter.start();
        root.addFilter(filter);
        context.updateLoggers();
        installed = true;
    }

    public void uninstall() {
        if (!installed) {
            return;
        }
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        LoggerConfig root = context.getConfiguration().getRootLogger();
        root.removeFilter(filter);
        filter.stop();
        context.updateLoggers();
        installed = false;
    }
}
