package com.uxplima.auth.velocity.adapter.inbound;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;

/**
 * Nothing on the proxy until login. A player who has not logged in runs none of the proxy's own commands but
 * the ones the operator allows, and is moved to no server but the first one and an auth server.
 *
 * <p>A command the proxy does not own goes on to the server the player is on, /login among them; that server's
 * door holds it as it holds every command there.
 */
public final class HoldListener {

    /** After the plugins that pick a server, before the claim is written for it. */
    private static final short HOLD = -500;

    private final ProxySessions sessions;
    private final CommandManager commands;
    private final Supplier<List<String>> allowed;
    private final Predicate<String> waitingRooms;
    private final Messages messages;

    /**
     * @param allowed the proxy commands a waiting player may run, without the slash
     * @param waitingRooms whether a server, by name, is one a waiting player may be sent to
     */
    public HoldListener(
            ProxySessions sessions,
            CommandManager commands,
            Supplier<List<String>> allowed,
            Predicate<String> waitingRooms,
            Messages messages) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.allowed = Objects.requireNonNull(allowed, "allowed");
        this.waitingRooms = Objects.requireNonNull(waitingRooms, "waitingRooms");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Subscribe(priority = HOLD)
    public void onCommand(CommandExecuteEvent event) {
        command(event);
    }

    @Subscribe(priority = HOLD)
    public void onSwitch(ServerPreConnectEvent event) {
        switched(event);
    }

    void command(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player) || sessions.loggedIn(player.getUniqueId())) {
            return;
        }
        String label = labelOf(event.getCommand());
        if (!commands.hasCommand(label) || allowed.get().contains(label)) {
            return;
        }
        event.setResult(CommandExecuteEvent.CommandResult.denied());
        player.sendMessage(messages.render(player, Keys.NETWORK_LOG_IN_FIRST));
    }

    void switched(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        if (!event.getResult().isAllowed()
                || sessions.loggedIn(player.getUniqueId())
                || event.getPreviousServer() == null) {
            return;
        }
        String target = event.getResult()
                .getServer()
                .map(server -> server.getServerInfo().getName())
                .orElse("");
        if (waitingRooms.test(target)) {
            return;
        }
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        player.sendMessage(messages.render(player, Keys.NETWORK_LOG_IN_FIRST));
    }

    private static String labelOf(String command) {
        String line = command.strip();
        int space = line.indexOf(' ');
        return (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
    }
}
