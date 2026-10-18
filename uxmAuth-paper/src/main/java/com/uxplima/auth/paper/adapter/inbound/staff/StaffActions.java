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

        @Override
        public CompletableFuture<Void> unregister(CommandSender sender, String name) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> setPassword(CommandSender sender, String name, String password) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> forceRegister(CommandSender sender, String name, String password) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> disableFactor(CommandSender sender, String name) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> markPremium(CommandSender sender, String name) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> markCracked(CommandSender sender, String name) {
            return CompletableFuture.completedFuture(null);
        }
    };

    /** Lets the waiting player of the account under this name in, on whichever server they wait. */
    CompletableFuture<Void> forceLogin(CommandSender sender, String name);

    /** Lifts the ban on an address and forgets its wrong passwords. */
    CompletableFuture<Void> unban(CommandSender sender, String address);

    /** Removes the account under this name, and sends its player away wherever they are. */
    CompletableFuture<Void> unregister(CommandSender sender, String name);

    /** Sets the password of the account under this name, and ends its sessions everywhere. */
    CompletableFuture<Void> setPassword(CommandSender sender, String name, String password);

    /** Makes a password account under this name, under the name and password rules. */
    CompletableFuture<Void> forceRegister(CommandSender sender, String name, String password);

    /** Turns off every second factor of the account under this name. */
    CompletableFuture<Void> disableFactor(CommandSender sender, String name);

    /** Turns the cracked account under this name premium, when Mojang names a paid account under it. */
    CompletableFuture<Void> markPremium(CommandSender sender, String name);

    /** Turns the premium account under this name cracked; it logs in with its password then. */
    CompletableFuture<Void> markCracked(CommandSender sender, String name);
}
