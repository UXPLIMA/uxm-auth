package com.uxplima.auth.paper.adapter.inbound.hold;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;

import com.uxplima.auth.paper.adapter.outbound.hold.HoldKeeper;

/**
 * Hears a {@code /deop} of a waiting player. The hold took that player off the operator list already, so the
 * command changes nothing there, and the hold must not give back what staff meant to take.
 *
 * <p>Only the command is heard, typed by the console or by somebody allowed to run it. A selector may reach any
 * waiting player, so it counts for every one: an operator wrongly left without the status asks for it again,
 * while one wrongly given it back keeps it. A plugin that takes the status through the API is not heard.
 */
public final class DeopWatch implements Listener {

    private static final String DEOP = "deop";
    private static final String PERMISSION = "minecraft.command.deop";

    private final HoldKeeper keeper;
    private final Function<String, Optional<UUID>> named;

    /** @param named the id of the player online under this name, or written as this id */
    public DeopWatch(HoldKeeper keeper, Function<String, Optional<UUID>> named) {
        this.keeper = Objects.requireNonNull(keeper, "keeper");
        this.named = Objects.requireNonNull(named, "named");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsole(ServerCommandEvent event) {
        heard(event.getSender(), event.getCommand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemote(RemoteServerCommandEvent event) {
        heard(event.getSender(), event.getCommand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayer(PlayerCommandPreprocessEvent event) {
        heard(event.getPlayer(), event.getMessage());
    }

    private void heard(CommandSender sender, String line) {
        String label = HoldListener.labelOf(line);
        if (!DEOP.equals(label.substring(label.indexOf(':') + 1)) || !sender.hasPermission(PERMISSION)) {
            return;
        }
        String[] words = line.strip().split("\\s+", 3);
        if (words.length < 2) {
            return;
        }
        String target = words[1];
        if (target.startsWith("@")) {
            keeper.withdrawEveryOp();
            return;
        }
        named.apply(target).ifPresent(keeper::withdrawOp);
    }
}
