package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import com.uxplima.auth.paper.adapter.inbound.mail.Recovery;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;

/** {@code /recover} and {@code /recover <code> <new password>}: a forgotten password, by a code to the address. */
@FromConfig(
        value = RecoverCommand.KEY,
        fallbackName = "recover",
        description = "Set a forgotten password by a code to your address")
public final class RecoverCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "recover";

    private static final String PERMISSION = "uxmauth.recover";

    private final Recovery recovery;

    public RecoverCommand(Recovery recovery) {
        this.recovery = Objects.requireNonNull(recovery, "recovery");
    }

    @Subcommand("")
    @Permission(PERMISSION)
    @PlayerOnly
    public void recover(
            Sender sender,
            @Arg(value = "code", optional = true) String code,
            @Arg(value = "password", optional = true) String fresh) {
        CompletableFuture<Void> unused = answer((Player) sender.bukkit(), code, fresh);
    }

    /** A code is asked with nothing typed, and recovered with the code and a new password. */
    CompletableFuture<Void> answer(Player player, String code, String fresh) {
        if (code.isBlank() && fresh.isBlank()) {
            return recovery.request(player);
        }
        return recovery.recover(player, code, fresh);
    }
}
