package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.concurrent.CompletableFuture;

import org.bukkit.command.CommandSender;

/** What the admin command hands staff: each answers once the sender has been told the outcome. */
public interface Staff {

    /** Answers nothing, for an admin command built without the tables behind it. */
    Staff NONE = new Staff() {
        @Override
        public CompletableFuture<Void> info(CommandSender sender, String name) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> history(CommandSender sender, String name) {
            return CompletableFuture.completedFuture(null);
        }
    };

    /** Everything staff read of the account under this name. */
    CompletableFuture<Void> info(CommandSender sender, String name);

    /** The latest logins of the account under this name. */
    CompletableFuture<Void> history(CommandSender sender, String name);
}
