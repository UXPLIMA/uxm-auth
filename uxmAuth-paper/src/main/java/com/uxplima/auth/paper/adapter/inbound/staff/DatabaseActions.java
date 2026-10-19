package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.concurrent.CompletableFuture;

import org.bukkit.command.CommandSender;

/** What the admin command hands the console to act on the database: each answers once the sender has been told. */
public interface DatabaseActions {

    /** Does nothing, for an admin command built without the database behind it. */
    DatabaseActions NONE = new DatabaseActions() {
        @Override
        public CompletableFuture<Void> move(CommandSender sender) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> scheme(CommandSender sender, String scheme) {
            return CompletableFuture.completedFuture(null);
        }
    };

    /** Copies every account to the database {@code storage.move-to} names, for a restart onto it. */
    CompletableFuture<Void> move(CommandSender sender);

    /** Gives every account the id the named UUID scheme gives it, and records that scheme. */
    CompletableFuture<Void> scheme(CommandSender sender, String scheme);
}
