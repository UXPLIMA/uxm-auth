package com.uxplima.auth.paper.adapter.inbound.screen;

import net.kyori.adventure.audience.Audience;

/** Puts a screen in front of a player, in the world or before it, and takes it away. */
public interface ScreenShower {

    void show(Audience who, DialogSpec spec);

    void close(Audience who);
}
