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
import com.uxplima.auth.application.factor.SecondStep;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.protection.Admission;
import com.uxplima.auth.domain.name.NameVerdict;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.DomainMark;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.state.AuthMethod;
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
         * player plays under another id than Mojang's. {@code visit} is the visit the proxy's claim named; a
         * connection with none is a visit of its own.
         */
        record Opens(
                UUID id, AuthState state, @Nullable PlayerProfile profile, List<Notice> notices, Optional<String> visit)
                implements Arrival {
            public Opens {
                Objects.requireNonNull(id, "id");
                Objects.requireNonNull(state, "state");
                notices = List.copyOf(Objects.requireNonNull(notices, "notices"));
                Objects.requireNonNull(visit, "visit");
            }

            /** A connection the proxy named no visit for. */
            public Opens(UUID id, AuthState state, @Nullable PlayerProfile profile, List<Notice> notices) {
                this(id, state, profile, notices, Optional.empty());
            }
        }

        /** Turned away, with the reason. */
        record Refused(Component reason) implements Arrival {
            public Refused {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    /** Whether an account back from this address, through this host, is let in by a session it still has. */
    @FunctionalInterface
    public interface Resume {
        boolean resumes(UUID account, String address, String host);
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
    private final NetworkDoor network;
    private final Admission admission;
    private final Resume resume;
    private final SecondStep secondStep;

    /**
     * @param mark the domain rule's mark, for a connection through the cracked address
     * @param scheme the UUID scheme this start recorded, which a cracked player plays under too
     * @param network the proxy's word, on a server behind it
     * @param admission the rules a connection meets before its name is read, on a server alone
     * @param resume whether an account back from this address through this host is let in by its session
     */
    public Arrivals(
            ReadTheName names,
            BedrockAdmission bedrock,
            HelloAtTheDoor hellos,
            Messages messages,
            Scheduler scheduler,
            BiFunction<UUID, String, PlayerProfile> profiles,
            Supplier<DomainMark> mark,
            UuidScheme scheme,
            NetworkDoor network,
            Admission admission,
            Resume resume) {
        this(
                names,
                bedrock,
                hellos,
                messages,
                scheduler,
                profiles,
                mark,
                scheme,
                network,
                admission,
                resume,
                SecondStep.NONE);
    }

    /** @param secondStep whether a way in that passed leads to the code of the second step */
    public Arrivals(
            ReadTheName names,
            BedrockAdmission bedrock,
            HelloAtTheDoor hellos,
            Messages messages,
            Scheduler scheduler,
            BiFunction<UUID, String, PlayerProfile> profiles,
            Supplier<DomainMark> mark,
            UuidScheme scheme,
            NetworkDoor network,
            Admission admission,
            Resume resume,
            SecondStep secondStep) {
        this.names = Objects.requireNonNull(names, "names");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.hellos = Objects.requireNonNull(hellos, "hellos");
        this.words = new DoorWords(Objects.requireNonNull(messages, "messages"));
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.mark = Objects.requireNonNull(mark, "mark");
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.network = Objects.requireNonNull(network, "network");
        this.admission = Objects.requireNonNull(admission, "admission");
        this.resume = Objects.requireNonNull(resume, "resume");
        this.secondStep = Objects.requireNonNull(secondStep, "secondStep");
    }

    /** What this pre-login comes to. */
    public Arrival arrive(AsyncPlayerPreLoginEvent event) {
        Objects.requireNonNull(event, "event");
        // Read first on every way in: the claim comes off the profile before anything else can see it.
        NetworkDoor.Reading vouched = network.read(event);
        // Behind a proxy the proxy's door already met the connection; alone, this door does.
        Optional<Admission.Refused> ruled = vouched instanceof NetworkDoor.Reading.None
                ? admission.decide(new Admission.Arrival(event.getAddress().getHostAddress(), event.getHostname()))
                : Optional.empty();
        if (ruled.isPresent()) {
            return new Arrival.Refused(words.admission(ruled.get()));
        }
        if (bedrock.vouches(event.getUniqueId())) {
            return bedrockArrival(event);
        }
        return vouched(event, vouched);
    }

    /** What the proxy's word, or its absence, comes to. */
    private Arrival vouched(AsyncPlayerPreLoginEvent event, NetworkDoor.Reading vouched) {
        UUID id = event.getUniqueId();
        return switch (vouched) {
            case NetworkDoor.Reading.None none -> alone(event);
            case NetworkDoor.Reading.LoggedIn in ->
                new Arrival.Opens(
                        id,
                        new AuthState.Authenticated(in.account(), in.method()),
                        null,
                        List.of(),
                        Optional.of(in.visit()));
            case NetworkDoor.Reading.Code code ->
                new Arrival.Opens(
                        id,
                        secondStep.awaiting(code.account(), code.method()),
                        null,
                        List.of(),
                        Optional.of(code.visit()));
            case NetworkDoor.Reading.Waiting waiting ->
                usualArrival(
                        event,
                        new HelloAtTheDoor.Reading.Usual(
                                waiting.flags().unverified(),
                                waiting.flags().explain(),
                                waiting.flags().marked()),
                        Optional.of(waiting.visit()));
            case NetworkDoor.Reading.Refused refused -> new Arrival.Refused(words.network());
        };
    }

    /** A server that stands alone, or a player the proxy sent no claim with: the hello decides. */
    private Arrival alone(AsyncPlayerPreLoginEvent event) {
        return switch (hellos.read(event.getName(), event.getUniqueId(), clientAddress(event))) {
            case HelloAtTheDoor.Reading.Refused refused ->
                new Arrival.Refused(words.refusal(refused.why(), refused.movedTo()));
            case HelloAtTheDoor.Reading.Premium premium -> premiumArrival(event, premium.state());
            case HelloAtTheDoor.Reading.Usual usual -> usualArrival(event, usual, Optional.empty());
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
                .map(this::stepped)
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
        return new Arrival.Opens(playAs, stepped(state), profile, List.of());
    }

    /** A way in that passed, as far as it goes: the whole login, or the code of the second step next. */
    private AuthState stepped(AuthState state) {
        return state instanceof AuthState.Authenticated in ? secondStep.after(in.account(), in.method(), null) : state;
    }

    /**
     * @param visit the visit the proxy's claim named, when the proxy sent the player: then the name already
     *     carries the mark, and the id the proxy gave a new name is the one it plays under
     */
    private Arrival usualArrival(
            AsyncPlayerPreLoginEvent event, HelloAtTheDoor.Reading.Usual usual, Optional<String> visit) {
        boolean networked = visit.isPresent();
        Optional<String> typed =
                networked && usual.marked() ? mark.get().unmark(event.getName()) : Optional.of(event.getName());
        if (typed.isEmpty()) {
            return new Arrival.Refused(words.network());
        }
        ReadTheName.Reading reading =
                usual.marked() ? names.readMarked(typed.get(), mark.get()) : names.read(typed.get());
        if (reading instanceof ReadTheName.Reading.Refused refused) {
            return new Arrival.Refused(words.name(refused.verdict()));
        }
        AuthState state = ((ReadTheName.Reading.Opens) reading).state();
        if (usual.unverified() && state instanceof AuthState.Unregistered unregistered) {
            state = new AuthState.Unregistered(unregistered.name(), true);
        }
        if (!networked
                && state instanceof AuthState.Registered registered
                && resume.resumes(
                        registered.account().id(), event.getAddress().getHostAddress(), event.getHostname())) {
            // Behind a proxy the proxy's door resumes a session; alone, this one does.
            state = secondStep.after(registered.account(), AuthMethod.SESSION, null);
        }
        return played(event, state, notices(usual, state), visit);
    }

    /** What the player reads once in: why the last try failed, and a move of their account they never read. */
    private List<Notice> notices(HelloAtTheDoor.Reading.Usual usual, AuthState state) {
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
        return notices;
    }

    /**
     * The id and name a player with a password plays under: an account's own, waiting or resumed by its session,
     * or the scheme's for a new name.
     * An offline server already derives the offline id from the name as typed, so a new name keeps it unless it
     * carries the mark or the scheme hands out its own.
     */
    private Arrival played(
            AsyncPlayerPreLoginEvent event, AuthState state, List<Notice> notices, Optional<String> visit) {
        boolean networked = visit.isPresent();
        PlayerName name;
        UUID id;
        if (state instanceof AuthState.Registered registered) {
            name = registered.account().name();
            id = registered.account().id();
        } else if (state instanceof AuthState.Authenticated resumed) {
            name = resumed.account().name();
            id = resumed.account().id();
        } else if (state instanceof AuthState.SecondFactor waiting) {
            name = waiting.account().name();
            id = waiting.account().id();
        } else {
            name = ((AuthState.Unregistered) state).name();
            boolean derived =
                    networked || (scheme != UuidScheme.RANDOM && name.value().equals(event.getName()));
            id = derived ? event.getUniqueId() : scheme.idFor(name, Optional.empty(), Optional.empty());
        }
        PlayerProfile profile = null;
        if (!id.equals(event.getUniqueId()) || !name.value().equals(event.getName())) {
            profile = profiles.apply(id, name.value());
            profile.setProperties(event.getPlayerProfile().getProperties());
        }
        return new Arrival.Opens(id, state, profile, notices, visit);
    }

    private static InetSocketAddress clientAddress(AsyncPlayerPreLoginEvent event) {
        PlayerConnection connection = event.getConnection();
        InetSocketAddress address = connection == null ? null : connection.getClientAddress();
        return address == null ? new InetSocketAddress(event.getAddress(), 0) : address;
    }
}
