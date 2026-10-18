package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import com.uxplima.auth.paper.adapter.inbound.factor.FactorSetup;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;

/**
 * {@code /2fa setup|confirm <code>|off <code>}: a logged-in player turns a code from an authenticator app after
 * the password on, and off again.
 */
@FromConfig(
        value = TwoFactorCommand.KEY,
        fallbackName = "2fa",
        description = "Ask a code from an app after your password")
public final class TwoFactorCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "2fa";

    private static final String PERMISSION = "uxmauth.2fa";

    private final FactorSetup setup;

    public TwoFactorCommand(FactorSetup setup) {
        this.setup = Objects.requireNonNull(setup, "setup");
    }

    @Subcommand("")
    @Permission(PERMISSION)
    @PlayerOnly
    public void usage(Sender sender) {
        setup.usage((Player) sender.bukkit());
    }

    @FromConfig(value = "setup", fallbackName = "setup", description = "Show the key, the link and the QR code")
    @Permission(PERMISSION)
    @PlayerOnly
    public void start(Sender sender) {
        CompletableFuture<Void> unused = setup.setup((Player) sender.bukkit());
    }

    @FromConfig(value = "confirm", fallbackName = "confirm", description = "Turn it on with the app's first code")
    @Permission(PERMISSION)
    @PlayerOnly
    public void confirm(Sender sender, @Arg("code") String code) {
        CompletableFuture<Void> unused = setup.confirm((Player) sender.bukkit(), code);
    }

    @FromConfig(value = "off", fallbackName = "off", description = "Turn it off with a code from the app")
    @Permission(PERMISSION)
    @PlayerOnly
    public void off(Sender sender, @Arg("code") String code) {
        CompletableFuture<Void> unused = setup.off((Player) sender.bukkit(), code);
    }
}
