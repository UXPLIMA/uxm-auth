package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import com.uxplima.auth.paper.adapter.inbound.factor.FactorLogin;
import com.uxplima.auth.paper.adapter.inbound.factor.FactorSetup;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;

/**
 * {@code /2fa setup|confirm <code>|off <code>}: a logged-in player turns a code from an authenticator app after
 * the password on, and off again. {@code /2fa <code>} is the code of the second step, after the password.
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
    private final FactorLogin login;

    public TwoFactorCommand(FactorSetup setup, FactorLogin login) {
        this.setup = Objects.requireNonNull(setup, "setup");
        this.login = Objects.requireNonNull(login, "login");
    }

    @Subcommand("")
    @Permission(PERMISSION)
    @PlayerOnly
    public void code(Sender sender, @Arg(value = "code", optional = true) String code) {
        CompletableFuture<Void> unused = answer((Player) sender.bukkit(), code);
    }

    /** The code of the second step, or what the command does when none is given. */
    CompletableFuture<Void> answer(Player player, String code) {
        if (code.isBlank()) {
            setup.usage(player);
            return CompletableFuture.completedFuture(null);
        }
        return login.answer(player, code);
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
