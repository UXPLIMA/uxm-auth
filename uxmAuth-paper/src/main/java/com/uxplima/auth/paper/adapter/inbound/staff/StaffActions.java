package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.concurrent.CompletableFuture;

import org.bukkit.command.CommandSender;

/** What the admin command hands staff to act on an account: each answers once the sender has been told. */
public interface StaffActions {

    /** Does nothing, for an admin command built without the tables behind it. */
    StaffActions NONE = new StaffActions() {
        @Override
        public CompletableFuture<Void> forceLogin(CommandSender sender, String name) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> unban(CommandSender sender, String address) {
            return CompletableFuture.completedFuture(null);
        }
    };

    /** Lets the waiting player of the account under this name in, on whichever server they wait. */
    CompletableFuture<Void> forceLogin(CommandSender sender, String name);

    /** Lifts the ban on an address and forgets its wrong passwords. */
    CompletableFuture<Void> unban(CommandSender sender, String address);
}
