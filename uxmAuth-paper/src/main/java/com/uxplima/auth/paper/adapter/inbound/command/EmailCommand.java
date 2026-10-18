package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import com.uxplima.auth.paper.adapter.inbound.mail.EmailLink;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;

/** {@code /email add <address>|confirm <code>|remove}: the address a forgotten password is sent to. */
@FromConfig(value = EmailCommand.KEY, fallbackName = "email", description = "Link an address for a forgotten password")
public final class EmailCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "email";

    private static final String PERMISSION = "uxmauth.email";

    private final EmailLink email;

    public EmailCommand(EmailLink email) {
        this.email = Objects.requireNonNull(email, "email");
    }

    @Subcommand("")
    @Permission(PERMISSION)
    @PlayerOnly
    public void usage(Sender sender) {
        CompletableFuture<Void> unused = email.usage((Player) sender.bukkit());
    }

    @FromConfig(value = "add", fallbackName = "add", description = "Send a code to an address")
    @Permission(PERMISSION)
    @PlayerOnly
    public void add(Sender sender, @Arg("address") String address) {
        CompletableFuture<Void> unused = email.add((Player) sender.bukkit(), address);
    }

    @FromConfig(value = "confirm", fallbackName = "confirm", description = "Link the address with its code")
    @Permission(PERMISSION)
    @PlayerOnly
    public void confirm(Sender sender, @Arg("code") String code) {
        CompletableFuture<Void> unused = email.confirm((Player) sender.bukkit(), code);
    }

    @FromConfig(value = "remove", fallbackName = "remove", description = "Take the linked address away")
    @Permission(PERMISSION)
    @PlayerOnly
    public void remove(Sender sender) {
        CompletableFuture<Void> unused = email.remove((Player) sender.bukkit());
    }
}
