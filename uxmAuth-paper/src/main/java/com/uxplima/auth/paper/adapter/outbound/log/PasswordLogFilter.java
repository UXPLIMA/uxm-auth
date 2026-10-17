package com.uxplima.auth.paper.adapter.outbound.log;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/**
 * Stops a log line that carries a password and writes it again without the password.
 *
 * <p>A Log4j filter can let a line through or stop it, not change it. So the line with the password is stopped
 * and the masked line is written in its place, under the same logger at the same level. The masked line ends in
 * the mask itself, so it passes this filter on its way through.
 */
public final class PasswordLogFilter extends AbstractFilter {

    private final PasswordMask mask;

    public PasswordLogFilter(PasswordMask mask) {
        this.mask = Objects.requireNonNull(mask, "mask");
    }

    @Override
    public Result filter(LogEvent event) {
        String line = event.getMessage().getFormattedMessage();
        if (line == null) {
            return Result.NEUTRAL;
        }
        String masked = mask.mask(line);
        if (masked.equals(line)) {
            return Result.NEUTRAL;
        }
        LogManager.getLogger(event.getLoggerName()).log(event.getLevel(), masked);
        return Result.DENY;
    }
}
