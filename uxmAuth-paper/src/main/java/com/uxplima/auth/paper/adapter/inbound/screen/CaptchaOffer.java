package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.UUID;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import org.jspecify.annotations.Nullable;

/** Shows a fresh captcha code to a reader under a connection, with a reason on top. */
@FunctionalInterface
public interface CaptchaOffer {

    /** Shows nothing, for a part built while no captcha is asked. */
    CaptchaOffer NONE = (reader, id, reason) -> {};

    void offer(Audience reader, UUID id, @Nullable Component reason);
}
