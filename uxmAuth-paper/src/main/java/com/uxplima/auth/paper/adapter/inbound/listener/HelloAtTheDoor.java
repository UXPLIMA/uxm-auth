package com.uxplima.auth.paper.adapter.inbound.listener;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.premium.AtTheDoor;
import com.uxplima.auth.application.premium.LetPremiumIn;
import com.uxplima.auth.application.premium.Moves;
import com.uxplima.auth.application.premium.PremiumAdmission;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.Decision;
import com.uxplima.auth.domain.premium.Refusal;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.handshake.HelloOutcome;
import org.jspecify.annotations.Nullable;

/**
 * What the hello of a Java connection came to, as its pre-login acts on it.
 *
 * <p>A connection Mojang proved is let into its premium account. One the door let through goes on to the name
 * check and the password as before, marked unverified or told why its last try failed when the door said so. A
 * refusal says why. Where the handshake is installed, a pre-login with no hello behind it is refused: every
 * connection passes the hello first, so a missing one is a connection that went around the door.
 */
public final class HelloAtTheDoor {

    /** What the pre-login does with the connection. */
    public sealed interface Reading {

        /** Go on to the name check and the password. */
        record Usual(boolean unverified, boolean explain) implements Reading {}

        /** In, logged in to this premium account. */
        record Premium(AuthState.Authenticated state) implements Reading {
            public Premium {
                Objects.requireNonNull(state, "state");
            }
        }

        /** Turned away; {@code movedTo} names the account that moved away from the name, when one did. */
        record Refused(Refusal why, Optional<PlayerName> movedTo) implements Reading {
            public Refused {
                Objects.requireNonNull(why, "why");
                Objects.requireNonNull(movedTo, "movedTo");
            }
        }
    }

    private final Hellos hellos;
    private final @Nullable AtTheDoor door;
    private final @Nullable LetPremiumIn premium;
    private final @Nullable Moves moves;

    /** No handshake: every connection goes on as usual. */
    public HelloAtTheDoor() {
        this.hellos = Hellos.NONE;
        this.door = null;
        this.premium = null;
        this.moves = null;
    }

    public HelloAtTheDoor(Hellos hellos, AtTheDoor door, LetPremiumIn premium, Moves moves) {
        this.hellos = Objects.requireNonNull(hellos, "hellos");
        this.door = Objects.requireNonNull(door, "door");
        this.premium = Objects.requireNonNull(premium, "premium");
        this.moves = Objects.requireNonNull(moves, "moves");
    }

    /** What the pre-login of the connection at this address, under this name and profile id, does. */
    public Reading read(String name, UUID profileId, InetSocketAddress address) {
        AtTheDoor mine = door;
        LetPremiumIn letIn = premium;
        if (!hellos.installed() || mine == null || letIn == null) {
            return new Reading.Usual(false, false);
        }
        Optional<HelloOutcome> outcome = hellos.take(address);
        String ip = address.getAddress().getHostAddress();
        return switch (outcome.orElse(null)) {
            case null -> new Reading.Refused(Refusal.LOOKUP_FAILED, Optional.empty());
            case HelloOutcome.NotProved notProved -> new Reading.Refused(Refusal.LOOKUP_FAILED, Optional.empty());
            case HelloOutcome.Proved proved -> admitted(mine, letIn, name, profileId, ip);
            case HelloOutcome.Decided decided -> decided(decided.decision(), name);
        };
    }

    private Reading admitted(AtTheDoor mine, LetPremiumIn letIn, String raw, UUID profileId, String ip) {
        PlayerName name = new PlayerName(raw);
        mine.passed(ip, name);
        return switch (letIn.admit(profileId, name, ip, mine.ownerAsked(ip, name))) {
            case PremiumAdmission.In in -> new Reading.Premium(in.state());
            case PremiumAdmission.Refused refused -> new Reading.Refused(refused.why(), Optional.empty());
        };
    }

    private Reading decided(Decision decision, String raw) {
        return switch (decision) {
            case Decision.Cracked cracked -> new Reading.Usual(cracked.unverified(), cracked.explain());
            case Decision.Refuse refuse ->
                new Reading.Refused(
                        refuse.why(),
                        refuse.why() == Refusal.PAID_NAME
                                ? Objects.requireNonNull(moves).movedTo(new PlayerName(raw))
                                : Optional.empty());
            case Decision.Handshake handshake -> new Reading.Refused(Refusal.LOOKUP_FAILED, Optional.empty());
        };
    }

    /** The move of this account its player has not read yet, where there is a handshake to move accounts. */
    public Optional<AccountRepository.Renamed> untoldMove(UUID accountId) {
        Moves mine = moves;
        return mine == null ? Optional.empty() : mine.untold(accountId);
    }

    /** The player of this account read of its move. */
    public void toldMove(UUID accountId) {
        Moves mine = moves;
        if (mine != null) {
            mine.told(accountId);
        }
    }
}
