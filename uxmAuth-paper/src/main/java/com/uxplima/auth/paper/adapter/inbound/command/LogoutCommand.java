package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.LogOut;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.text.message.Messages;

/** {@code /logout}: back to the door, held again until the next login. */
@FromConfig(value = LogoutCommand.KEY, fallbackName = "logout", description = "Log out and wait at the door again")
public final class LogoutCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "logout";

    private final LogOut logOut;
    private final Door door;
    private final MomentRunner moments;
    private final Messages messages;

    public LogoutCommand(LogOut logOut, Door door, MomentRunner moments, Messages messages) {
        this.logOut = Objects.requireNonNull(logOut, "logOut");
        this.door = Objects.requireNonNull(door, "door");
        this.moments = Objects.requireNonNull(moments, "moments");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Subcommand("")
    @Permission("uxmauth.logout")
    @PlayerOnly
    public void logout(Sender sender) {
        attempt((Player) sender.bukkit());
    }

    void attempt(Player player) {
        switch (logOut.run(player.getUniqueId())) {
            case NOT_LOGGED_IN -> messages.send(player, Keys.LOGOUT_NOT_LOGGED_IN);
            // No password to come back with: the way back in is the one they came by, a new join.
            case NO_PASSWORD -> player.kick(messages.render(player, Keys.KICK_LOGGED_OUT));
            case LOGGED_OUT -> {
                moments.fire(player, AuthSettings.Moment.LOGGED_OUT);
                door.hold(player);
            }
        }
    }
}
