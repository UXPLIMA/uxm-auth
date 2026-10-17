package com.uxplima.auth.paper.adapter.inbound.screen;

import org.bukkit.entity.Player;

/** Where a press on a Bedrock form goes: the same answers a dialog's press reaches. */
@FunctionalInterface
public interface FormAnswers {

    void answer(Player player, String action, ScreenAnswers.Answer answer);
}
