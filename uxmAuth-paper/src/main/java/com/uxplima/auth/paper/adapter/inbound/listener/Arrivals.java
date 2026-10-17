package com.uxplima.auth.paper.adapter.inbound.listener;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import io.papermc.paper.connection.PlayerConnection;

import net.kyori.adventure.text.Component;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.uxplima.auth.adapter.outbound.DoorWords;
import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.name.NameVerdict;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.DomainMark;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.hold.Notices;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * What the pre-login of a connection comes to: the state it opens with and the id it plays under, or the reason
 * it is turned away.
 *
 * <p>A connection Floodgate vouches for takes the Bedrock door. Every other one reads its hello first: proved by
 * Mojang, it is in its premium account; let through, it goes to the name check and the password; refused, it
 * reads why. This runs on the thread the server gives the pre-login, which may wait on the database.
 */
public final class Arrivals {

    /** What the connection comes to. */
    public sealed interface Arrival {

        /**
         * In the door, with this state under this id. {@code profile} replaces the one the server made, when the
         * player plays under another id than Mojang's.
         */
        record Opens(UUID id, AuthState state, @Nullable PlayerProfile profile, List<Notice> notices)
                implements Arrival {
            public Opens {
                Objects.requireNonNull(id, "id");
                Objects.requireNonNull(state, "state");
                notices = List.copyOf(Objects.requireNonNull(notices, "notices"));
            }
        }

        /** Turned away, with the reason. */
        record Refused(Component reason) implements Arrival {
            public Refused {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    /** Something the player is told once they stand in the world, before or after their login. */
    public record Notice(Notices.Notice notice, boolean afterLogin) {
        public Notice {
            Objects.requireNonNull(notice, "notice");
        }
    }

    private final ReadTheName names;
    private final BedrockAdmission bedrock;
    private final HelloAtTheDoor hellos;
    private final DoorWords words;
    private final Scheduler scheduler;
    private final BiFunction<UUID, String, PlayerProfile> profiles;
    private final Supplier<DomainMark> mark;
    private final UuidScheme scheme;

    /**
     * @param mark the domain rule's mark, for a connection through the cracked address
     * @param scheme the UUID scheme this start recorded, which a cracked player plays under too
     */
    public Arrivals(
            ReadTheName names,
            BedrockAdmission bedrock,
            HelloAtTheDoor hellos,
            Messages messages,
            Scheduler scheduler,
            BiFunction<UUID, String, PlayerProfile> profiles,
            Supplier<DomainMark> mark,
            UuidScheme scheme) {
        this.names = Objects.requireNonNull(names, "names");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.hellos = Objects.requireNonNull(hellos, "hellos");
        this.words = new DoorWords(Objects.requireNonNull(messages, "messages"));
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.mark = Objects.requireNonNull(mark, "mark");
        this.scheme = Objects.requireNonNull(scheme, "scheme");
    }

    /** What this pre-login comes to. */
    public Arrival arrive(AsyncPlayerPreLoginEvent event) {
        Objects.requireNonNull(event, "event");
        UUID id = event.getUniqueId();
        if (bedrock.vouches(id)) {
            return bedrockArrival(event);
        }
        return switch (hellos.read(event.getName(), id, clientAddress(event))) {
            case HelloAtTheDoor.Reading.Refused refused ->
                new Arrival.Refused(words.refusal(refused.why(), refused.movedTo()));
            case HelloAtTheDoor.Reading.Premium premium -> premiumArrival(event, premium.state());
            case HelloAtTheDoor.Reading.Usual usual -> usualArrival(event, usual);
        };
    }

    /** What a connection under a name the door already let through waits for, for a player online at enable. */
    public AuthState waiting(PlayerName name) {
        return names.waiting(name);
    }

    private Arrival bedrockArrival(AsyncPlayerPreLoginEvent event) {
        Optional<NameVerdict> blocked = names.readBedrock(event.getName());
        if (blocked.isPresent()) {
            return new Arrival.Refused(words.name(blocked.get()));
        }
        PlayerName name = new PlayerName(event.getName());
        AuthState state = bedrock.admit(
                        event.getUniqueId(), name, event.getAddress().getHostAddress())
                .orElseGet(() -> names.waiting(name));
        return new Arrival.Opens(event.getUniqueId(), state, null, List.of());
    }

    /** Mojang's profile, under the account's own id when that is not Mojang's, skin and all. */
    private Arrival premiumArrival(AsyncPlayerPreLoginEvent event, AuthState.Authenticated state) {
        UUID playAs = state.account().id();
        PlayerProfile profile = null;
        if (!playAs.equals(event.getUniqueId())) {
            profile = profiles.apply(playAs, event.getName());
            profile.setProperties(event.getPlayerProfile().getProperties());
        }
        return new Arrival.Opens(playAs, state, profile, List.of());
    }

    private Arrival usualArrival(AsyncPlayerPreLoginEvent event, HelloAtTheDoor.Reading.Usual usual) {
        ReadTheName.Reading reading =
                usual.marked() ? names.readMarked(event.getName(), mark.get()) : names.read(event.getName());
        if (reading instanceof ReadTheName.Reading.Refused refused) {
            return new Arrival.Refused(words.name(refused.verdict()));
        }
        AuthState state = ((ReadTheName.Reading.Opens) reading).state();
        if (usual.unverified() && state instanceof AuthState.Unregistered unregistered) {
            state = new AuthState.Unregistered(unregistered.name(), true);
        }
        List<Notice> notices = new java.util.ArrayList<>();
        if (usual.explain()) {
            notices.add(new Notice(new Notices.Notice(Keys.LOGIN_HANDSHAKE_FAILED, List.of(), () -> {}), false));
        }
        if (state instanceof AuthState.Registered registered) {
            UUID account = registered.account().id();
            hellos.untoldMove(account)
                    .ifPresent(move -> notices.add(new Notice(
                            new Notices.Notice(
                                    Keys.LOGIN_RENAMED,
                                    List.of(
                                            Text.placeholder("old", move.from().value()),
                                            Text.placeholder("new", move.to().value())),
                                    () -> scheduler.async(() -> hellos.toldMove(account))),
                            true)));
        }
        return played(event, state, notices);
    }

    /**
     * The id and name a player with a password plays under: an account's own, or the scheme's for a new name.
     * An offline server already derives the offline id from the name as typed, so a new name keeps it unless it
     * carries the mark or the scheme hands out its own.
     */
    private Arrival played(AsyncPlayerPreLoginEvent event, AuthState state, List<Notice> notices) {
        PlayerName name;
        UUID id;
        if (state instanceof AuthState.Registered registered) {
            name = registered.account().name();
            id = registered.account().id();
        } else {
            name = ((AuthState.Unregistered) state).name();
            boolean derived = scheme != UuidScheme.RANDOM && name.value().equals(event.getName());
            id = derived ? event.getUniqueId() : scheme.idFor(name, Optional.empty(), Optional.empty());
        }
        PlayerProfile profile = null;
        if (!id.equals(event.getUniqueId()) || !name.value().equals(event.getName())) {
            profile = profiles.apply(id, name.value());
            profile.setProperties(event.getPlayerProfile().getProperties());
        }
        return new Arrival.Opens(id, state, profile, notices);
    }

    private static InetSocketAddress clientAddress(AsyncPlayerPreLoginEvent event) {
        PlayerConnection connection = event.getConnection();
        InetSocketAddress address = connection == null ? null : connection.getClientAddress();
        return address == null ? new InetSocketAddress(event.getAddress(), 0) : address;
    }
}
