package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.AuthSettings;

/**
 * The one a screen answers: a player standing in the world, or a client still connecting that has no player
 * yet. The answers are the same for both; only what "let in" and "kick" mean differs.
 */
public interface Guest {

    /** The connection the session is kept under. */
    UUID id();

    /** The opening of the session this guest answers for, when it is bound to one; empty means the current. */
    OptionalLong opening();

    /** The name the client logged in with. */
    String name();

    /** The address the client came from, as the account records it. */
    String address();

    /** Where screens and their words go, read in this guest's language. */
    Audience audience();

    /** Whether the guest holds a permission. A connection with no player yet holds what everyone holds. */
    boolean may(String permission);

    /** Runs the effects of a moment, where the guest can see them. */
    void fire(AuthSettings.Moment moment);

    /** Sends the guest away with a reason. */
    void kick(Component reason);

    /** The guest is in: whatever held them lets go, and the moment of their way in runs. */
    void letIn(AuthSettings.Moment moment);

    /** The guest closed the screen and screens.conf lets them into the frozen world to use the commands. */
    void release();

    /** Hands the answer of {@code work} to {@code then} on the thread this guest is served from. */
    <T> CompletableFuture<Void> after(CompletableFuture<T> work, Consumer<T> then);
}
