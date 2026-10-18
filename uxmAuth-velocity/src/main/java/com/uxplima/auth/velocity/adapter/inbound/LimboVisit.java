package com.uxplima.auth.velocity.adapter.inbound;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Claim;

/**
 * One player's stay in the proxy's waiting world, from arrival to the moment they are let out logged in or sent
 * away. It names no LimboAPI type: the world tells it what the player said, and it tells the world what to do.
 */
public final class LimboVisit {

    /** What the waiting world does for the visit. */
    public interface Exit {

        /** Show the player this line. */
        void tell(Component text);

        /** Let the player out, logged in, to the server the proxy picks. */
        void release();

        /** Send the player away from the network with this reason. */
        void sendAway(Component reason);
    }

    private static final Claim.Waiting NOTHING = new Claim.Waiting(false, false, false);

    private final LimboLogin login;
    private final ProxySessions sessions;
    private final Remember remember;
    private final UUID id;
    private final PlayerName name;
    private final String address;
    private final String host;
    private final Audience reader;
    private final Exit exit;
    private final AtomicBoolean over = new AtomicBoolean();

    public LimboVisit(
            LimboLogin login,
            ProxySessions sessions,
            Remember remember,
            UUID id,
            PlayerName name,
            String address,
            String host,
            Audience reader,
            Exit exit) {
        this.login = Objects.requireNonNull(login, "login");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.remember = Objects.requireNonNull(remember, "remember");
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.address = Objects.requireNonNull(address, "address");
        this.host = Objects.requireNonNull(host, "host");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.exit = Objects.requireNonNull(exit, "exit");
    }

    /** The player arrived in the waiting world. */
    public void arrive() {
        login.arrive(reader, id, name, sessions.door(id).orElse(NOTHING), host).forEach(exit::tell);
    }

    /** Tell the player again what to type. */
    public void remind() {
        if (!over.get()) {
            exit.tell(login.reminder(reader, id));
        }
    }

    /** Whether the player has an account, so the time to log in rather than to register applies. */
    public boolean registered() {
        return login.registered(id);
    }

    /** The player said this line; the answer comes once the use case has. */
    public CompletableFuture<Void> said(String line) {
        Objects.requireNonNull(line, "line");
        return login.say(reader, id, name, line, address).thenAccept(this::answer);
    }

    /** The time to log in ran out: a player still waiting is sent away. */
    public void timeUp(Component reason) {
        Objects.requireNonNull(reason, "reason");
        if (over.compareAndSet(false, true)) {
            login.leave(id);
            exit.sendAway(reason);
        }
    }

    /** The player left the waiting world, however they left it. */
    public void left() {
        over.set(true);
        login.leave(id);
    }

    private void answer(LimboLogin.Outcome outcome) {
        switch (outcome) {
            case LimboLogin.Outcome.Stay stay -> exit.tell(stay.said());
            case LimboLogin.Outcome.In in -> {
                if (over.compareAndSet(false, true)) {
                    sessions.loggedIn(id, in.method());
                    remember.loggedIn(id, address, host);
                    login.leave(id);
                    exit.release();
                }
            }
            case LimboLogin.Outcome.Out out -> {
                if (over.compareAndSet(false, true)) {
                    login.leave(id);
                    exit.sendAway(out.reason());
                }
            }
        }
    }
}
