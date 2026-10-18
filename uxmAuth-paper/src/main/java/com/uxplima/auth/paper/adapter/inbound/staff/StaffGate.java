package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.staff.StaffAction;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The console runs every staff command; a player runs a destructive one only when the operator opened it in game.
 * A hijacked staff account then cannot unregister the rest.
 */
public final class StaffGate {

    private final Predicate<StaffAction> openInGame;
    private final Messages messages;

    /** @param openInGame whether staff in game may run an action, read from the settings of the moment */
    public StaffGate(Predicate<StaffAction> openInGame, Messages messages) {
        this.openInGame = Objects.requireNonNull(openInGame, "openInGame");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Whether the sender may run the action; a refused player is told how to open it. */
    public boolean allows(CommandSender sender, StaffAction action) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(action, "action");
        if (!(sender instanceof Player) || openInGame.test(action)) {
            return true;
        }
        messages.send(sender, Keys.STAFF_CONSOLE_ONLY, Text.placeholder("command", action.key()));
        return false;
    }
}
