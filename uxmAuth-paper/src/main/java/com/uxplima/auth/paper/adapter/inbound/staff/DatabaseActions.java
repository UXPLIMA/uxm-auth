package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.concurrent.CompletableFuture;

import org.bukkit.command.CommandSender;

/** What the admin command hands the console to act on the database: each answers once the sender has been told. */
public interface DatabaseActions {

    /** Does nothing, for an admin command built without the database behind it. */
    DatabaseActions NONE = sender -> CompletableFuture.completedFuture(null);

    /** Copies every account to the database {@code storage.move-to} names, for a restart onto it. */
    CompletableFuture<Void> move(CommandSender sender);
}
