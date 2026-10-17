package com.uxplima.auth.application.port;

import com.uxplima.auth.application.premium.PremiumAnswer;
import com.uxplima.auth.domain.name.PlayerName;

/**
 * One place on the web that knows whether a name is a paid account's.
 *
 * <p>A source never throws for a failure of its own: a refusal, a limit or a dead line answers
 * {@link PremiumAnswer.Unknown}, so the next source is asked.
 */
@FunctionalInterface
public interface NameSource {

    /** What this source says about the name. It may wait on the network. */
    PremiumAnswer ask(PlayerName name);
}
